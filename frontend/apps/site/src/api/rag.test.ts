import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  RagApiError,
  parseSseFrame,
  ragErrorCopy,
  resolveRagBaseUrl,
  splitSseFrames,
  streamPublicRagAnswer,
  toStreamEvent
} from './rag';

const encoder = new TextEncoder();

/** 用给定的原始 SSE 文本片段构造一个 Response 替身（分块模拟真实网络到达顺序）。 */
function streamResponse(chunks: string[], init: { status?: number; contentType?: string } = {}) {
  const status = init.status ?? 200;
  const contentType = init.contentType ?? 'text/event-stream';
  let index = 0;

  const body = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index >= chunks.length) {
        controller.close();
        return;
      }
      controller.enqueue(encoder.encode(chunks[index]));
      index += 1;
    }
  });

  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': contentType }),
    body,
    json: async () => ({})
  };
}

function jsonErrorResponse(status: number, payload: unknown) {
  return {
    ok: false,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    body: null,
    json: async () => payload
  };
}

async function collect(stream: AsyncGenerator<unknown>): Promise<unknown[]> {
  const events: unknown[] = [];
  for await (const event of stream) events.push(event);
  return events;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('splitSseFrames', () => {
  it('splits complete frames and keeps the unfinished tail', () => {
    const { frames, rest } = splitSseFrames(
      'data: {"type":"delta","text":"a"}\n\ndata: {"type":"del'
    );

    expect(frames).toEqual(['data: {"type":"delta","text":"a"}']);
    expect(rest).toBe('data: {"type":"del');
  });

  it('treats CRLF blank lines as frame separators', () => {
    const { frames } = splitSseFrames('data: {"type":"delta","text":"a"}\r\n\r\ndata: {}\r\n\r\n');

    expect(frames).toEqual(['data: {"type":"delta","text":"a"}', 'data: {}']);
  });

  it('does not cut a frame when the separator is split across chunks', () => {
    const first = splitSseFrames('data: {"type":"delta","text":"a"}\n');
    expect(first.frames).toEqual([]);
    expect(first.rest).toBe('data: {"type":"delta","text":"a"}\n');

    const second = splitSseFrames(`${first.rest}\ndata: {"type":"done"}`);
    expect(second.frames).toEqual(['data: {"type":"delta","text":"a"}']);
    expect(second.rest).toBe('data: {"type":"done"}');
  });
});

describe('parseSseFrame', () => {
  it('parses a delta event', () => {
    expect(parseSseFrame('data: {"type":"delta","text":"你好"}')).toEqual({
      type: 'delta',
      text: '你好'
    });
  });

  it('ignores comment, event and id lines', () => {
    expect(parseSseFrame(': keep-alive')).toBeNull();
    expect(parseSseFrame('event: ping\ndata: ')).toBeNull();
  });

  it('drops malformed JSON instead of throwing', () => {
    expect(parseSseFrame('data: {"type":"delta"')).toBeNull();
  });

  it('joins multi-line data payloads', () => {
    expect(parseSseFrame('data: {"type":"delta",\ndata: "text":"x"}')).toEqual({
      type: 'delta',
      text: 'x'
    });
  });

  it('normalizes a citation event with missing optional fields', () => {
    const event = parseSseFrame(
      'data: {"type":"citation","citation":{"chunkId":"c1","sequence":3}}'
    );

    expect(event).toEqual({
      type: 'citation',
      citation: {
        index: 0,
        chunkId: 'c1',
        documentId: '',
        documentTitle: '未命名文档',
        headingPath: null,
        sourcePath: null,
        articleSlug: null,
        sequence: 3,
        quote: '',
        quoteVerified: false
      }
    });
  });

  it('parses a done event into terminal metadata', () => {
    const event = parseSseFrame(
      'data: {"type":"done","refused":true,"refusalReason":"model_refused","degraded":false,"latencyMs":1200}'
    );

    expect(event).toEqual({
      type: 'done',
      refused: true,
      refusalReason: 'model_refused',
      refusalDetail: null,
      degraded: false,
      degradedReason: null,
      chatModel: null,
      latencyMs: 1200,
      firstDeltaMs: null
    });
  });

  it('parses an error event and falls back for missing fields', () => {
    expect(parseSseFrame('data: {"type":"error","code":"server_busy","message":"忙"}')).toEqual({
      type: 'error',
      code: 'server_busy',
      message: '忙'
    });
    expect(parseSseFrame('data: {"type":"error"}')).toEqual({
      type: 'error',
      code: 'rag_stream_error',
      message: '问答服务出现异常。'
    });
  });

  it('rejects unknown event types and non-object payloads', () => {
    expect(parseSseFrame('data: {"type":"ping"}')).toBeNull();
    expect(toStreamEvent('delta')).toBeNull();
    expect(toStreamEvent(null)).toBeNull();
  });

  it('maps known error codes to Chinese copy', () => {
    expect(ragErrorCopy('rag_rate_limited', 'fallback')).toBe('提问太频繁了，请稍后再试。');
    expect(ragErrorCopy('unknown_code', 'fallback')).toBe('fallback');
  });
});

describe('resolveRagBaseUrl', () => {
  it('returns empty string when env is not configured', () => {
    expect(resolveRagBaseUrl()).toBe('');
  });
});

describe('streamPublicRagAnswer', () => {
  it('defaults baseUrl to empty string when not provided', async () => {
    const fetchMock = vi.fn(async () =>
      streamResponse(['data: {"type":"done","refused":false}\n\n'])
    );
    vi.stubGlobal('fetch', fetchMock);

    await collect(streamPublicRagAnswer({ query: 'q' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/public/rag/answer/stream',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ query: 'q' })
      })
    );
  });

  it('yields delta, citation and done in order across chunk boundaries', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'data: {"type":"delta","text":"你"}\n\ndata: {"type":"del',
          'ta","text":"好"}\n\n',
          'data: {"type":"citation","citation":{"chunkId":"c1","sequence":2}}\n\n',
          'data: {"type":"done","refused":false,"latencyMs":900}\n\n'
        ])
      )
    );

    const events = await collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }));

    expect(events).toEqual([
      { type: 'delta', text: '你' },
      { type: 'delta', text: '好' },
      { type: 'citation', citation: expect.objectContaining({ chunkId: 'c1', sequence: 2 }) },
      {
        type: 'done',
        refused: false,
        latencyMs: 900,
        refusalReason: null,
        refusalDetail: null,
        degraded: false,
        degradedReason: null,
        chatModel: null,
        firstDeltaMs: null
      }
    ]);
  });

  it('stops consuming after the terminal event', async () => {
    const fetchMock = vi.fn(async () =>
      streamResponse([
        'data: {"type":"done","refused":false}\n\n',
        'data: {"type":"delta","text":"late"}\n\n'
      ])
    );
    vi.stubGlobal('fetch', fetchMock);

    const events = await collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }));

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ type: 'done' });
  });

  it('posts the query to the public stream endpoint', async () => {
    const fetchMock = vi.fn(async () =>
      streamResponse(['data: {"type":"done","refused":false}\n\n'])
    );
    vi.stubGlobal('fetch', fetchMock);

    await collect(streamPublicRagAnswer({ query: '切片重叠区是什么', baseUrl: 'http://api/' }));

    expect(fetchMock).toHaveBeenCalledWith(
      'http://api/api/v1/public/rag/answer/stream',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ query: '切片重叠区是什么' })
      })
    );
  });

  it('accepts both the event stream and the JSON error body', async () => {
    const fetchMock = vi.fn(async () =>
      streamResponse(['data: {"type":"done","refused":false}\n\n'])
    );
    vi.stubGlobal('fetch', fetchMock);

    await collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }));

    // 正常响应是 SSE 流、预检失败是 JSON 错误体，双值让两条路径都能协商；后端 9e27590
    // 修复了「单一 SSE 值时预检错误退化成 403 + 空体」的问题，双值保留以免依赖该修复。
    const [, init] = fetchMock.mock.calls[0] as unknown as [
      string,
      { headers: Record<string, string> }
    ];
    expect(init.headers.Accept).toBe('text/event-stream, application/json');
  });

  it('throws a typed error for a JSON 429 preflight response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonErrorResponse(429, { code: 'rag_rate_limited', message: 'Too many requests' })
      )
    );

    await expect(
      collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }))
    ).rejects.toMatchObject({
      name: 'RagApiError',
      code: 'rag_rate_limited',
      status: 429,
      message: '提问太频繁了，请稍后再试。'
    });
  });

  it('throws a typed error for a 404 when public Q&A is disabled', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonErrorResponse(404, { code: 'rag_public_disabled', message: 'off' }))
    );

    await expect(
      collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }))
    ).rejects.toBeInstanceOf(RagApiError);
  });

  it('rejects a 200 response that is not an event stream', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => streamResponse(['{"code":"success"}'], { contentType: 'application/json' }))
    );

    await expect(
      collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }))
    ).rejects.toMatchObject({ code: 'rag_unexpected_response' });
  });

  it('ends quietly when the stream closes before a terminal event', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => streamResponse(['data: {"type":"delta","text":"半"}']))
    );

    const events = await collect(streamPublicRagAnswer({ query: 'q', baseUrl: 'http://api' }));

    expect(events).toEqual([{ type: 'delta', text: '半' }]);
  });
});
