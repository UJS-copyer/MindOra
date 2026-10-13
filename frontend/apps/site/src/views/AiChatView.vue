<script setup lang="ts">
/**
 * 公开问答页（Stage 3.4 / T5）。
 *
 * 消费后端 `POST /api/v1/public/rag/answer/stream`（SSE：delta → citation → done），
 * 展示流式答案、知识库引用回链、拒答 / 降级 / 限流状态。
 * 业务逻辑全部在 `useRagChat`，本组件只负责绑定与滚动。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import ChatMessage from '../components/chat/ChatMessage.vue';
import {
  QUERY_MAX_LENGTH,
  useRagChat,
  type RagChatMessage
} from '../composables/useRagChat';

const props = defineProps<{
  onOpenCitation: (slug: string, headingPath: string | null) => void;
}>();

const STORAGE_KEY = 'mindora-rag-chat-history';
const HISTORY_LIMIT = 20;

const { messages, isStreaming, cooldownSeconds, canSend, send, abort, clear } = useRagChat();

const inputQuery = ref('');
const chatContainerRef = ref<HTMLElement | null>(null);

const presetPrompts = [
  '这个知识库目前收录了哪些内容？',
  '知识库的检索是怎么工作的？',
  '怎么把我的笔记同步进知识库？'
];

const lastMessage = computed<RagChatMessage | null>(
  () => messages.value[messages.value.length - 1] ?? null
);

/** 只在「消息条数 / 最后一条内容长度 / 状态」变化时滚动，避免每次渲染都滚。 */
const scrollSignal = computed(
  () =>
    `${messages.value.length}:${lastMessage.value?.content.length ?? 0}:${lastMessage.value?.status ?? ''}`
);

const showPresets = computed(() => messages.value.length === 0);

const canSubmit = computed(() => canSend.value && inputQuery.value.trim().length > 0);

const placeholder = computed(() =>
  cooldownSeconds.value > 0
    ? `触发限流，${cooldownSeconds.value} 秒后可再次提问`
    : '提出你的疑问，例如：这个知识库的检索链路是怎样工作的？'
);

function scrollToBottom() {
  void nextTick(() => {
    const container = chatContainerRef.value;
    if (container) container.scrollTop = container.scrollHeight;
  });
}

function loadHistory() {
  try {
    const cached = localStorage.getItem(STORAGE_KEY);
    if (!cached) return;
    const parsed: unknown = JSON.parse(cached);
    if (!Array.isArray(parsed)) return;
    // 只恢复了结的问答；流式中间态与错误态不入库，避免重开页面出现半截回答。
    messages.value = (parsed as RagChatMessage[])
      .filter((item) => item && item.role && item.status === 'done' && typeof item.content === 'string')
      .slice(-HISTORY_LIMIT);
  } catch {
    messages.value = [];
  }
}

function saveHistory() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(messages.value.filter((item) => item.status === 'done').slice(-HISTORY_LIMIT))
    );
  } catch {
    /* localStorage 不可用（隐私模式 / 配额）时静默跳过 */
  }
}

function handleSend(preset?: string) {
  const query = (preset ?? inputQuery.value).trim();
  if (!query || !canSend.value) return;
  inputQuery.value = '';
  void send(query);
}

function handleClear() {
  clear();
  saveHistory();
}

function handleOpenCitation(payload: { slug: string; headingPath: string | null }) {
  props.onOpenCitation(payload.slug, payload.headingPath);
}

watch(scrollSignal, scrollToBottom, { flush: 'post' });
watch(isStreaming, (streaming) => {
  if (!streaming) saveHistory();
});

onMounted(() => {
  loadHistory();
  scrollToBottom();
});
</script>

<template>
  <div class="max-w-4xl mx-auto h-[calc(100vh-8.5rem)] flex flex-col glass-card rounded-2xl overflow-hidden relative">
    <!-- 顶部对话头信息 -->
    <div class="px-6 py-3.5 border-b border-zinc-200/60 dark:border-zinc-800 flex items-center justify-between flex-shrink-0 bg-white/40 dark:bg-zinc-900/40">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-purple-600 text-white flex items-center justify-center font-bold text-sm shadow-md shadow-purple-500/20">
          AI
        </div>
        <div>
          <div class="font-semibold text-xs text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
            MindOra 公开知识库问答
            <span class="text-[10px] px-1.5 py-0.2 rounded-full bg-emerald-500/10 text-emerald-600 font-mono">
              scope=public
            </span>
          </div>
          <div class="text-[11px] text-zinc-400">仅基于公开文档作答，答案附引用来源，可回链原文</div>
        </div>
      </div>

      <button
        type="button"
        class="text-xs px-2.5 py-1 rounded-lg text-zinc-500 hover:text-red-600 hover:bg-red-500/10 transition-colors cursor-pointer"
        title="清空对话"
        @click="handleClear"
      >
        清空对话
      </button>
    </div>

    <!-- 消息对话流 -->
    <div ref="chatContainerRef" class="flex-1 overflow-y-auto p-6 space-y-6">
      <!-- 预设问题（仅在空会话时展示） -->
      <div v-if="showPresets" class="p-4 rounded-xl bg-purple-500/5 border border-purple-500/10">
        <div class="text-xs font-semibold text-purple-700 dark:text-purple-300 mb-2 flex items-center gap-1.5">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
          你可以试着这样问我：
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <button
            v-for="prompt in presetPrompts"
            :key="prompt"
            type="button"
            class="p-2 text-left rounded-lg glass-card hover:border-purple-500/40 text-xs text-zinc-700 dark:text-zinc-300 transition-all cursor-pointer truncate"
            @click="handleSend(prompt)"
          >
            "{{ prompt }}"
          </button>
        </div>
        <p class="mt-2.5 text-[11px] text-zinc-400 leading-relaxed">
          公开知识库目前只收录已发布的公开文档。若知识库里没有依据，我会直接说明而不是猜测。
        </p>
      </div>

      <ChatMessage
        v-for="message in messages"
        :key="message.id"
        :message="message"
        @open-citation="handleOpenCitation"
      />
    </div>

    <!-- 底部输入栏 -->
    <div class="p-4 border-t border-zinc-200/60 dark:border-zinc-800 bg-white/40 dark:bg-zinc-900/40">
      <form class="flex items-center gap-2" @submit.prevent="handleSend()">
        <input
          v-model="inputQuery"
          type="text"
          :placeholder="placeholder"
          :maxlength="QUERY_MAX_LENGTH"
          class="flex-1 px-4 py-2.5 rounded-xl glass-card border-0 outline-none text-xs text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 focus:ring-1 focus:ring-emerald-500 disabled:opacity-60"
          :disabled="isStreaming || cooldownSeconds > 0"
        />

        <button
          v-if="isStreaming"
          type="button"
          class="px-5 py-2.5 rounded-xl bg-zinc-700 hover:bg-zinc-600 text-white font-medium text-xs transition-colors cursor-pointer flex items-center gap-1.5 shadow-sm"
          @click="abort()"
        >
          <span class="w-2 h-2 rounded-sm bg-white"></span>
          <span>停止</span>
        </button>

        <button
          v-else
          type="submit"
          class="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-medium text-xs transition-colors cursor-pointer flex items-center gap-1.5 shadow-sm"
          :disabled="!canSubmit"
        >
          <span>发送</span>
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
      </form>
    </div>
  </div>
</template>
