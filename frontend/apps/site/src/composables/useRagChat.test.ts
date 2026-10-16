import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  DEGRADED_COPY,
  QUERY_LENGTH_COPY,
  RATE_LIMIT_WINDOW_SECONDS,
  refusalCopyFor,
  useRagChat
} from './useRagChat';

const encoder = new TextEncoder();

function streamResponse(chunks: string[]) {
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
    ok: true,
    status: 200,
    headers: new Headers({ 'content-type': 'text/event-stream' }),
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

/** 永不产出数据的事件流：用于验证 abort 路径。 */
function hangingResponse(signal?: AbortSignal) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({ 'content-type': 'text/event-stream' }),
    body: {
      getReader: () => ({
        read: () =>
          new Promise((_resolve, reject) => {
            signal?.addEventListener('abort', () => {
              const error = new Error('aborted');
              error.name = 'AbortError';
              reject(error);
            });
          }),
        cancel: async () => undefined
      })
    },
    json: async () => ({})
  };
}

function createChat() {
  return useRagChat({ baseUrl: 'http://api' });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useRagChat', () => {
  it('accumulates deltas, collects citations and finishes as done', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'data: {"type":"delta","text":"检索"}\n\n',
          'data: {"type":"delta","text":"成功"}\n\n',
          'data: {"type":"citation","citation":{"chunkId":"c1","sequence":2,"articleSlug":"reading-route"}}\n\n',
          'data: {"type":"citation","citation":{"chunkId":"c1","sequence":2}}\n\n',
          'data: {"type":"done","refused":false,"chatModel":"Qwen3-30B","latencyMs":1200,"firstDeltaMs":900}\n\n'
        ])
      )
    );

    const chat = createChat();
    await chat.send('检索是怎么做的？');

    expect(chat.messages.value).toHaveLength(2);
    const [user, assistant] = chat.messages.value;
    expect(user.content).toBe('检索是怎么做的？');
    expect(assistant.content).toBe('检索成功');
    expect(assistant.status).toBe('done');
    // 同一 chunk + sequence 的重复引用被去重
    expect(assistant.citations).toHaveLength(1);
    expect(assistant.citations[0]).toMatchObject({ chunkId: 'c1', articleSlug: 'reading-route' });
    expect(assistant.meta).toMatchObject({
      chatModel: 'Qwen3-30B',
      latencyMs: 1200,
      firstDeltaMs: 900
    });
    expect(chat.isStreaming.value).toBe(false);
    expect(chat.canSend.value).toBe(true);
  });

  it('replaces already-streamed text when done reports a refusal', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'data: {"type":"delta","text":"这是一段未经引用核验的草稿"}\n\n',
          'data: {"type":"citation","citation":{"chunkId":"c1","sequence":1}}\n\n',
          'data: {"type":"done","refused":true,"refusalReason":"invalid_citation","refusalDetail":"quote mismatch"}\n\n'
        ])
      )
    );

    const chat = createChat();
    await chat.send('引用核验会失败吗？');

    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('refused');
    expect(assistant.content).toBe(refusalCopyFor('invalid_citation'));
    expect(assistant.content).not.toContain('草稿');
    expect(assistant.citations).toEqual([]);
    expect(assistant.meta?.refusalDetail).toBe('quote mismatch');
  });

  it('falls back to the generic refusal copy for an unknown reason', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'data: {"type":"done","refused":true,"refusalReason":"brand_new_reason"}\n\n'
        ])
      )
    );

    const chat = createChat();
    await chat.send('未知拒答原因');

    expect(chat.messages.value[1].content).toBe(refusalCopyFor('brand_new_reason'));
  });

  it('uses the degraded copy when the model is unavailable and no delta arrives', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        streamResponse([
          'data: {"type":"citation","citation":{"chunkId":"c9","sequence":7}}\n\n',
          'data: {"type":"done","refused":false,"degraded":true,"degradedReason":"rag_chat_unavailable","firstDeltaMs":-1}\n\n'
        ])
      )
    );

    const chat = createChat();
    await chat.send('模型不可用时怎么办？');

    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('degraded');
    expect(assistant.content).toBe(DEGRADED_COPY);
    expect(assistant.citations).toHaveLength(1);
    expect(assistant.meta?.degradedReason).toBe('rag_chat_unavailable');
  });

  it('surfaces a rate limit with a cooldown and blocks further sends', async () => {
    const fetchMock = vi.fn(async () =>
      jsonErrorResponse(429, { code: 'rag_rate_limited', message: 'Too many requests' })
    );
    vi.stubGlobal('fetch', fetchMock);

    const chat = createChat();
    await chat.send('第一次提问');

    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('rate_limited');
    expect(chat.cooldownSeconds.value).toBe(RATE_LIMIT_WINDOW_SECONDS);
    expect(chat.canSend.value).toBe(false);

    await chat.send('冷却期内不应发出');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(chat.messages.value).toHaveLength(2);

    chat.dispose();
  });

  it('reports a neutral invalid status without calling the backend', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const chat = createChat();
    await chat.send('短');

    // §11.8-F2：长度校验是用法提示而非失败，状态为中性 invalid（徽标「提示」），
    // 不再复用红色「出错了」的 error 态
    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('invalid');
    expect(assistant.content).toBe(QUERY_LENGTH_COPY);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('marks the answer as cancelled when aborted mid-stream', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: { signal?: AbortSignal }) => hangingResponse(init?.signal))
    );

    const chat = createChat();
    const pending = chat.send('中途停止');
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(chat.isStreaming.value).toBe(true);
    chat.abort();
    await pending;

    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('cancelled');
    expect(chat.isStreaming.value).toBe(false);
  });

  it('treats a stream closed without a terminal event as an error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => streamResponse(['data: {"type":"delta","text":"半截"}']))
    );

    const chat = createChat();
    await chat.send('传输中断');

    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('error');
    expect(assistant.content).not.toBe('半截');
    expect(assistant.content).toContain('传输中断开');
  });

  it('reports a network failure as an error message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      })
    );

    const chat = createChat();
    await chat.send('网络异常');

    const assistant = chat.messages.value[1];
    expect(assistant.status).toBe('error');
    expect(assistant.content).toContain('网络请求失败');
  });

  it('clears the conversation', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => streamResponse(['data: {"type":"done","refused":false}\n\n']))
    );

    const chat = createChat();
    await chat.send('清空对话');
    expect(chat.messages.value).toHaveLength(2);

    chat.clear();
    expect(chat.messages.value).toHaveLength(0);
  });
});
