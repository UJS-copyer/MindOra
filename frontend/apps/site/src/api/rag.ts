/**
 * 公开问答（RAG）传输层：SSE 帧解析与流式消费。
 *
 * 契约来源：`docs/design-11-rag-answer-generation-layer.md` §10–§12（Stage 3.4 后端交付，
 * 见 `docs/stage-3.4-task-manifest-cn.md` §10.1）。
 *
 * 类型来源分两类：
 * - **引用与请求结构走生成契约**（`@mindora/types` 的 `components['schemas']`），
 *   字段改名/增删会在编译期暴露；
 * - **SSE 事件不在 OpenAPI 里**（端点返回 `SseEmitter`，契约只有空的 `SseEmitter` 结构），
 *   因此事件判别联合仍按 design-11 §12 手工镜像，并在此显式说明。
 *
 * 三条实现约束（design-11 §12.1，均已核实）：
 * 1. 不能用 `EventSource`——端点是 POST；必须 `fetch` + `response.body.getReader()` 自行切帧；
 * 2. 预检失败（400/404/429）返回**普通 JSON**，必须先判状态码与 `content-type`；
 * 3. 429 没有 `Retry-After`，节流提示由前端按配置窗口自行给出。
 */
import type { components } from '@mindora/types';

type ContractCitation = components['schemas']['CitationView'];
type ContractPublicRagRequest = components['schemas']['PublicRagRequestView'];

/** 契约里可选、界面需要「有值或显式 null」的字段。 */
type Nullable<T> = Exclude<T, undefined> | null;

export type PublicRagRequest = ContractPublicRagRequest;

/**
 * 规范化后的引用：wire 形状（`ContractCitation`）字段全可选，界面需要确定值，
 * 因此在 `Pick`/`Required` 之上派生——字段一旦在契约里改名，这里会编译失败。
 */
export type RagCitation = Required<
  Pick<
    ContractCitation,
    'index' | 'chunkId' | 'documentId' | 'documentTitle' | 'sequence' | 'quote' | 'quoteVerified'
  >
> & {
  /** 可空的展示字段：缺失时界面按「无」处理，不参与回链判定。 */
  headingPath: Nullable<ContractCitation['headingPath']>;
  sourcePath: Nullable<ContractCitation['sourcePath']>;
  /** 文章 slug；为 null 表示该知识文档没有对应的公开博客文章，无法回链。 */
  articleSlug: Nullable<ContractCitation['articleSlug']>;
};

/** `done` 事件携带的终态元信息（design-11 §12；SSE 事件不在 OpenAPI 契约里）。 */
export interface RagStreamMeta {
  refused: boolean;
  refusalReason: string | null;
  refusalDetail: string | null;
  degraded: boolean;
  degradedReason: string | null;
  chatModel: string | null;
  latencyMs: number | null;
  firstDeltaMs: number | null;
}

/** SSE 事件判别联合（design-11 §12 的终版契约）。 */
export type RagStreamEvent =
  | { type: 'delta'; text: string }
  | { type: 'citation'; citation: RagCitation }
  | ({ type: 'done' } & RagStreamMeta)
  | { type: 'error'; code: string; message: string };

/** 预检失败（普通 JSON 4xx）与流开始前的本地失败。 */
export class RagApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, status: number, message: string) {
    super(message);
    this.name = 'RagApiError';
    this.code = code;
    this.status = status;
  }
}

/** 同源相对路径；绝对地址会让登录态 Cookie 变第三方，正常不要配置。 */
export const DEFAULT_RAG_BASE_URL = '';

/** 与 `App.vue` 一致的基地址解析（`VITE_API_BASE_URL`，缺省同源相对路径；绝对地址会让登录态 Cookie 变第三方，正常不要配置）。 */
export function resolveRagBaseUrl(): string {
  const configured = import.meta.env?.VITE_API_BASE_URL;
  return typeof configured === 'string' && configured.trim().length > 0
    ? configured
    : DEFAULT_RAG_BASE_URL;
}

/** 已知错误码的中文文案；未知错误码沿用后端 `message`。 */
const RAG_ERROR_COPY: Record<string, string> = {
  validation_error: '问题长度需要在 2 到 500 字之间。',
  rag_rate_limited: '提问太频繁了，请稍后再试。',
  rag_public_disabled: '公开问答当前未开放。'
};

export function ragErrorCopy(code: string, fallback: string): string {
  return RAG_ERROR_COPY[code] ?? fallback;
}

const FRAME_SEPARATORS = ['\r\n\r\n', '\n\n'];

/**
 * 按空行切出完整 SSE 帧，并返回尚未收尾的残余文本。
 * 残余必须与后续数据块拼接后再切，否则跨块的帧会被解析成坏 JSON。
 */
export function splitSseFrames(buffer: string): { frames: string[]; rest: string } {
  const frames: string[] = [];
  let cursor = 0;

  for (;;) {
    let cut = -1;
    let cutLength = 0;
    for (const separator of FRAME_SEPARATORS) {
      const index = buffer.indexOf(separator, cursor);
      if (index >= 0 && (cut < 0 || index < cut)) {
        cut = index;
        cutLength = separator.length;
      }
    }
    if (cut < 0) break;
    frames.push(buffer.slice(cursor, cut));
    cursor = cut + cutLength;
  }

  return { frames, rest: buffer.slice(cursor) };
}

/**
 * 解析单个 SSE 帧。
 * 非 `data:` 行（`event:` / `id:` / 注释）、坏 JSON、未知 `type` 一律返回 null——
 * 丢弃但不中断流，避免单帧异常毁掉整次问答。
 */
export function parseSseFrame(frame: string): RagStreamEvent | null {
  const payload = frame
    .split(/\r\n|\r|\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice('data:'.length).trim())
    .join('\n');

  if (!payload) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    return null;
  }
  return toStreamEvent(parsed);
}

/** 把已 JSON 解析的值收敛成判别联合；形状不对时返回 null。 */
export function toStreamEvent(value: unknown): RagStreamEvent | null {
  if (typeof value !== 'object' || value === null) return null;
  const record = value as Record<string, unknown>;

  switch (record.type) {
    case 'delta':
      return typeof record.text === 'string' ? { type: 'delta', text: record.text } : null;
    case 'citation':
      return typeof record.citation === 'object' && record.citation !== null
        ? {
            type: 'citation',
            citation: normalizeCitation(record.citation as Record<string, unknown>)
          }
        : null;
    case 'done':
      return { type: 'done', ...normalizeMeta(record) };
    case 'error':
      return {
        type: 'error',
        code: typeof record.code === 'string' && record.code ? record.code : 'rag_stream_error',
        message:
          typeof record.message === 'string' && record.message
            ? record.message
            : '问答服务出现异常。'
      };
    default:
      return null;
  }
}

function readText(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function normalizeCitation(value: Record<string, unknown>): RagCitation {
  return {
    index: readNumber(value.index) ?? 0,
    chunkId: readText(value.chunkId) ?? '',
    documentId: readText(value.documentId) ?? '',
    documentTitle: readText(value.documentTitle) ?? '未命名文档',
    headingPath: readText(value.headingPath),
    sourcePath: readText(value.sourcePath),
    articleSlug: readText(value.articleSlug),
    sequence: readNumber(value.sequence) ?? 0,
    quote: readText(value.quote) ?? '',
    quoteVerified: value.quoteVerified === true
  };
}

function normalizeMeta(record: Record<string, unknown>): RagStreamMeta {
  return {
    refused: record.refused === true,
    refusalReason: readText(record.refusalReason),
    refusalDetail: readText(record.refusalDetail),
    degraded: record.degraded === true,
    degradedReason: readText(record.degradedReason),
    chatModel: readText(record.chatModel),
    latencyMs: readNumber(record.latencyMs),
    firstDeltaMs: readNumber(record.firstDeltaMs)
  };
}

/**
 * 预检失败体 → 类型化错误。
 * 后端返回 `{ code, message }`（`ApiErrorEnvelope`）；不是 JSON 时保留兜底文案。
 */
async function toRagApiError(
  response: Response,
  fallbackCode = 'rag_request_failed'
): Promise<RagApiError> {
  let code = fallbackCode;
  let message = `请求失败（HTTP ${response.status}）。`;

  try {
    const body = (await response.json()) as { code?: unknown; message?: unknown };
    const bodyCode = readText(body?.code);
    const bodyMessage = readText(body?.message);
    if (bodyCode) code = bodyCode;
    if (bodyMessage) message = bodyMessage;
  } catch {
    /* 响应体不是 JSON：保留兜底文案 */
  }

  return new RagApiError(code, response.status, ragErrorCopy(code, message));
}

export interface PublicRagStreamOptions {
  query: string;
  baseUrl?: string;
  signal?: AbortSignal;
}

/**
 * 以 SSE 消费 `POST /api/v1/public/rag/answer/stream`。
 *
 * 迭代在 `done` 或 `error` 后结束（终态恰一个）；若流被提前关闭而没有终态，
 * 迭代会正常结束，由调用方判定「传输中断」。
 */
export async function* streamPublicRagAnswer(
  options: PublicRagStreamOptions
): AsyncGenerator<RagStreamEvent, void, void> {
  const baseUrl = (options.baseUrl ?? resolveRagBaseUrl()).replace(/\/+$/, '');

  const response = await fetch(`${baseUrl}/api/v1/public/rag/answer/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // 同时声明两种类型：正常响应是 SSE 流，预检失败（400/429）返回的却是 JSON 错误体
      // （design-11 §12.1-2），双值让两条路径都能完成内容协商。
      // 背景：2026-10-13 实机发现只声明 `text/event-stream` 时预检错误会退化成 403 + 空体，
      // 拿不到 `rag_rate_limited` 等错误码；后端已在 9e27590 修复（错误响应显式
      // application/json + 放行 /error），单一 SSE 值现已可用，此处保留双值以免依赖该修复，
      // 详见 docs/stage-3.4-task-manifest-cn.md §11.7。
      Accept: 'text/event-stream, application/json'
    },
    body: JSON.stringify({ query: options.query } satisfies PublicRagRequest),
    signal: options.signal
  });

  if (!response.ok) {
    throw await toRagApiError(response);
  }
  if (!(response.headers.get('content-type') ?? '').includes('text/event-stream')) {
    throw await toRagApiError(response, 'rag_unexpected_response');
  }

  const stream = response.body;
  if (!stream) {
    throw new RagApiError('rag_stream_unavailable', response.status, '当前环境不支持流式响应。');
  }

  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let terminal = false;

  try {
    while (!terminal) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const { frames, rest } = splitSseFrames(buffer);
      buffer = rest;

      for (const frame of frames) {
        const event = parseSseFrame(frame);
        if (!event) continue;
        terminal = event.type === 'done' || event.type === 'error';
        yield event;
        if (terminal) break;
      }
    }

    if (!terminal) {
      buffer += decoder.decode();
      const trailing = parseSseFrame(buffer);
      if (trailing) yield trailing;
    }
  } finally {
    try {
      await reader.cancel();
    } catch {
      /* 流已正常结束或已被取消 */
    }
  }
}
