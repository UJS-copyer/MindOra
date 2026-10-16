/**
 * 公开问答页状态机。
 *
 * 视图层（`views/AiChatView.vue`）只绑定这里的状态，不再持有任何问答业务逻辑；
 * 后续 T6（会话历史 / `conversationId` 多轮）只需扩展本文件。
 *
 * 关键契约（`docs/design-11-rag-answer-generation-layer.md` §12）：
 * 「已流出的文字随后被判定为拒答」时，**前端必须用标准拒答文案替换已显示内容**——
 * 严格模式下引用逐字核验需要完整文本，因此 `done{refused=true}` 可能晚于已显示的文字。
 * 这是设计取舍，不是缺陷。
 */
import { computed, getCurrentScope, onScopeDispose, reactive, ref } from 'vue';
import {
  RagApiError,
  resolveRagBaseUrl,
  streamPublicRagAnswer,
  type RagCitation,
  type RagStreamEvent,
  type RagStreamMeta
} from '../api/rag';

export type RagMessageStatus =
  | 'streaming'
  | 'done'
  | 'refused'
  | 'degraded'
  | 'error'
  | 'rate_limited'
  | 'cancelled'
  | 'invalid';

export interface RagChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  citations: RagCitation[];
  status: RagMessageStatus;
  createdAt: number;
  meta: RagStreamMeta | null;
}

/** 与后端 `mindora.rag.public.window-seconds` 默认值一致（design-11 §10.1）。 */
export const RATE_LIMIT_WINDOW_SECONDS = 60;

/** 与后端公开端点请求校验一致（2~500 字，design-11 §10）。 */
export const QUERY_MIN_LENGTH = 2;
export const QUERY_MAX_LENGTH = 500;

/**
 * 拒答文案按 `refusalReason` 分派。
 * `no_context` / `model_refused` / `invalid_citation` 来自 `RefusalReason` 枚举；
 * `protected_content` 来自 Stage 3.4 的 SecretGuard 上下文层（design-11 §11）。
 */
const REFUSAL_COPY: Record<string, string> = {
  no_context:
    '公开知识库里没有检索到与这个问题相关的内容。可以换一种问法，或先浏览博客中的公开文章。',
  model_refused: '现有公开内容不足以可靠回答这个问题，这里不作推测。',
  invalid_citation: '这次生成的内容没有通过引用核验，为保证可信度不予展示。',
  protected_content: '检索到的片段包含不适合公开展示的信息，已停止作答。'
};

const REFUSAL_FALLBACK_COPY = '公开知识库里没有足够可靠的依据来回答这个问题，我就不猜测了。';

export function refusalCopyFor(reason: string | null): string {
  return (reason && REFUSAL_COPY[reason]) || REFUSAL_FALLBACK_COPY;
}

export const DEGRADED_COPY = '对话模型当前不可用。以下是与问题最相关的知识片段，供你直接阅读。';

const ERROR_COPY: Record<string, string> = {
  server_busy: '问答服务正在排队，请稍后重试。',
  rag_stream_unavailable: '当前环境不支持流式响应，请更换浏览器后重试。'
};

const NETWORK_ERROR_COPY = '网络请求失败，未能完成这次问答，请稍后重试。';
const INTERRUPTED_COPY = '这次回答在传输中断开了，请稍后重试。';
const CANCELLED_COPY = '已停止本次回答。';
export const QUERY_LENGTH_COPY = `问题长度需要在 ${QUERY_MIN_LENGTH} 到 ${QUERY_MAX_LENGTH} 字之间。`;

function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    (error as { name?: unknown }).name === 'AbortError'
  );
}

export interface UseRagChatOptions {
  baseUrl?: string;
}

export function useRagChat(options: UseRagChatOptions = {}) {
  const baseUrl = options.baseUrl ?? resolveRagBaseUrl();

  const messages = ref<RagChatMessage[]>([]);
  const isStreaming = ref(false);
  const cooldownSeconds = ref(0);

  let controller: AbortController | null = null;
  let cooldownTimer: ReturnType<typeof setInterval> | null = null;
  let idSeed = 0;

  const canSend = computed(() => !isStreaming.value && cooldownSeconds.value === 0);

  function nextId(prefix: string): string {
    idSeed += 1;
    return `${prefix}-${Date.now()}-${idSeed}`;
  }

  function pushMessage(role: RagChatMessage['role'], status: RagMessageStatus): RagChatMessage {
    const message = reactive<RagChatMessage>({
      id: nextId(role),
      role,
      content: '',
      citations: [],
      status,
      createdAt: Date.now(),
      meta: null
    });
    messages.value.push(message);
    return message;
  }

  function stopCooldown() {
    if (cooldownTimer !== null) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
  }

  function startCooldown(seconds: number) {
    stopCooldown();
    cooldownSeconds.value = seconds;
    cooldownTimer = setInterval(() => {
      cooldownSeconds.value = Math.max(0, cooldownSeconds.value - 1);
      if (cooldownSeconds.value === 0) stopCooldown();
    }, 1000);
  }

  function applyEvent(message: RagChatMessage, event: RagStreamEvent) {
    switch (event.type) {
      case 'delta':
        if (event.text) message.content += event.text;
        break;

      case 'citation':
        if (
          !message.citations.some(
            (item) =>
              item.chunkId === event.citation.chunkId && item.sequence === event.citation.sequence
          )
        ) {
          message.citations.push(event.citation);
        }
        break;

      case 'done': {
        const { type: _type, ...meta } = event;
        message.meta = meta;

        if (event.refused) {
          // 已流出的文字必须被替换：这段内容没有通过引用核验，不能留在界面上。
          message.content = refusalCopyFor(event.refusalReason);
          message.citations = [];
          message.status = 'refused';
        } else if (event.degraded) {
          // 降级路径不产生 delta（`firstDeltaMs=-1`），正文需要兜底提示语。
          if (!message.content.trim()) message.content = DEGRADED_COPY;
          message.status = 'degraded';
        } else {
          message.status = 'done';
        }
        break;
      }

      case 'error':
        message.content = ERROR_COPY[event.code] ?? event.message;
        message.status = 'error';
        break;
    }
  }

  async function send(rawQuery: string): Promise<void> {
    const query = rawQuery.trim();
    if (!query || !canSend.value) return;

    const userMessage = pushMessage('user', 'done');
    userMessage.content = query;
    const assistant = pushMessage('assistant', 'streaming');

    if (query.length < QUERY_MIN_LENGTH || query.length > QUERY_MAX_LENGTH) {
      // 客户端用法提示不是请求失败：用中性 `invalid` 态渲染（§11.8-F2），
      // 与真正的传输/服务端错误（红色「出错了」）区分开；不打任何网络请求。
      assistant.status = 'invalid';
      assistant.content = QUERY_LENGTH_COPY;
      return;
    }

    isStreaming.value = true;
    controller = new AbortController();
    let terminal = false;

    try {
      for await (const event of streamPublicRagAnswer({
        query,
        baseUrl,
        signal: controller.signal
      })) {
        applyEvent(assistant, event);
        if (event.type === 'done' || event.type === 'error') {
          terminal = true;
          break;
        }
      }
      if (!terminal) {
        assistant.status = 'error';
        assistant.content = INTERRUPTED_COPY;
      }
    } catch (error) {
      if (isAbortError(error)) {
        assistant.status = 'cancelled';
        if (!assistant.content.trim()) assistant.content = CANCELLED_COPY;
      } else if (error instanceof RagApiError) {
        if (error.code === 'rag_rate_limited') {
          assistant.status = 'rate_limited';
          assistant.content = `提问太频繁了，请在 ${RATE_LIMIT_WINDOW_SECONDS} 秒后重试。`;
          startCooldown(RATE_LIMIT_WINDOW_SECONDS);
        } else {
          assistant.status = 'error';
          assistant.content = error.message;
        }
      } else {
        assistant.status = 'error';
        assistant.content = NETWORK_ERROR_COPY;
      }
    } finally {
      isStreaming.value = false;
      controller = null;
    }
  }

  /** 中断当前流（用户点「停止」或离开页面）。后端据此记 `status=cancelled`。 */
  function abort() {
    controller?.abort();
  }

  function clear() {
    abort();
    messages.value = [];
  }

  function dispose() {
    abort();
    stopCooldown();
  }

  // 在组件 / effect scope 内使用时自动清理；在测试中直接调用则跳过。
  if (getCurrentScope()) {
    onScopeDispose(dispose);
  }

  return {
    messages,
    isStreaming,
    cooldownSeconds,
    canSend,
    baseUrl,
    send,
    abort,
    clear,
    dispose
  };
}
