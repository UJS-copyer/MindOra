<script setup lang="ts">
import { computed } from 'vue';
import type { RagChatMessage, RagMessageStatus } from '../../composables/useRagChat';
import CitationCard from './CitationCard.vue';

const props = defineProps<{
  message: RagChatMessage;
}>();

const emit = defineEmits<{
  openCitation: [payload: { slug: string; headingPath: string | null }];
}>();

function handleCitationOpen(payload: { slug: string; headingPath: string | null }) {
  emit('openCitation', payload);
}

const isAssistant = computed(() => props.message.role === 'assistant');

const statusBadge = computed<{ label: string; tone: string } | null>(() => {
  const tones: Record<RagMessageStatus, { label: string; tone: string } | null> = {
    streaming: null,
    done: null,
    refused: { label: '已拒答', tone: 'bg-amber-500/10 text-amber-600 dark:text-amber-400' },
    degraded: { label: '降级模式', tone: 'bg-zinc-500/10 text-zinc-500 dark:text-zinc-400' },
    error: { label: '出错了', tone: 'bg-red-500/10 text-red-600 dark:text-red-400' },
    rate_limited: { label: '触发限流', tone: 'bg-amber-500/10 text-amber-600 dark:text-amber-400' },
    cancelled: { label: '已停止', tone: 'bg-zinc-500/10 text-zinc-500 dark:text-zinc-400' },
    // 客户端输入校验（如问题长度）：用法提示，不是失败态（§11.8-F2）
    invalid: { label: '提示', tone: 'bg-zinc-500/10 text-zinc-500 dark:text-zinc-400' }
  };
  return tones[props.message.status];
});

const metaLine = computed(() => {
  const meta = props.message.meta;
  if (!meta) return '';
  const parts: string[] = [];
  if (meta.chatModel) parts.push(meta.chatModel);
  if (typeof meta.latencyMs === 'number') parts.push(`${meta.latencyMs} ms`);
  if (typeof meta.firstDeltaMs === 'number' && meta.firstDeltaMs >= 0) {
    parts.push(`首字 ${meta.firstDeltaMs} ms`);
  }
  return parts.join(' · ');
});
</script>

<template>
  <div class="flex gap-3" :class="isAssistant ? 'justify-start' : 'justify-end'">
    <div
      v-if="isAssistant"
      class="w-7 h-7 rounded-lg bg-purple-600 text-white flex-shrink-0 flex items-center justify-center font-bold text-xs"
    >
      AI
    </div>

    <div class="max-w-[85%] sm:max-w-[75%] space-y-2">
      <div
        class="p-4 rounded-2xl text-xs leading-relaxed"
        :class="
          isAssistant
            ? 'glass-card text-zinc-800 dark:text-zinc-200 rounded-bl-xs'
            : 'bg-emerald-600 text-white rounded-br-xs'
        "
      >
        <div class="flex items-center gap-2 mb-1.5" v-if="isAssistant && statusBadge">
          <span
            class="text-[10px] px-1.5 py-0.2 rounded-full font-medium"
            :class="statusBadge.tone"
          >
            {{ statusBadge.label }}
          </span>
        </div>

        <p v-if="message.content" class="whitespace-pre-wrap">{{ message.content }}</p>

        <div v-if="message.status === 'streaming'" class="flex items-center gap-1.5 py-0.5">
          <div class="w-1.5 h-1.5 rounded-full bg-purple-500 animate-bounce"></div>
          <div
            class="w-1.5 h-1.5 rounded-full bg-purple-500 animate-bounce [animation-delay:0.2s]"
          ></div>
          <div
            class="w-1.5 h-1.5 rounded-full bg-purple-500 animate-bounce [animation-delay:0.4s]"
          ></div>
          <span v-if="!message.content" class="text-zinc-400 ml-1">
            正在检索公开知识库并组织回答…
          </span>
        </div>
      </div>

      <!-- 引用来源（仅助手消息） -->
      <div v-if="isAssistant && message.citations.length" class="space-y-1.5 pt-1">
        <div class="text-[11px] font-semibold text-zinc-400 flex items-center gap-1">
          <svg
            class="w-3 h-3 text-emerald-500"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
          </svg>
          知识库引用来源 ({{ message.citations.length }})
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <CitationCard
            v-for="citation in message.citations"
            :key="`${citation.chunkId}-${citation.sequence}`"
            :citation="citation"
            @open="handleCitationOpen"
          />
        </div>
      </div>

      <div v-if="isAssistant && metaLine" class="text-[10px] text-zinc-400 font-mono pl-1 truncate">
        {{ metaLine }}
      </div>
    </div>

    <div
      v-if="!isAssistant"
      class="w-7 h-7 rounded-lg bg-zinc-700 text-white flex-shrink-0 flex items-center justify-center font-bold text-xs"
    >
      我
    </div>
  </div>
</template>
