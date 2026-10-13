<script setup lang="ts">
import type { RagCitation } from '../../api/rag';

const props = defineProps<{
  citation: RagCitation;
}>();

const emit = defineEmits<{
  open: [payload: { slug: string; headingPath: string | null }];
}>();

function handleOpen() {
  if (props.citation.articleSlug) {
    emit('open', { slug: props.citation.articleSlug, headingPath: props.citation.headingPath });
  }
}
</script>

<template>
  <div
    class="p-3 rounded-xl border transition-colors group"
    :class="
      citation.articleSlug
        ? 'border-zinc-200/60 dark:border-zinc-800 bg-white/60 dark:bg-zinc-900/60 hover:border-emerald-500/50 cursor-pointer'
        : 'border-zinc-200/60 dark:border-zinc-800 bg-white/40 dark:bg-zinc-900/40'
    "
    :role="citation.articleSlug ? 'button' : undefined"
    :tabindex="citation.articleSlug ? 0 : undefined"
    @click="handleOpen"
    @keydown.enter="handleOpen"
  >
    <div class="flex items-start justify-between gap-2 text-[11px] mb-1.5">
      <div class="flex items-center gap-1.5 min-w-0">
        <span
          class="flex-shrink-0 px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-mono font-semibold"
        >
          [{{ citation.index }}]
        </span>
        <span
          class="font-medium text-zinc-800 dark:text-zinc-200 truncate group-hover:text-emerald-600 dark:group-hover:text-emerald-400"
        >
          {{ citation.documentTitle }}
        </span>
      </div>
      <span
        class="flex-shrink-0 font-mono text-[10px] px-1.5 py-0.2 rounded"
        :class="
          citation.quoteVerified
            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
            : 'bg-zinc-500/10 text-zinc-500'
        "
      >
        {{ citation.quoteVerified ? '原文引用' : '服务端摘录' }}
      </span>
    </div>

    <div
      v-if="citation.headingPath"
      class="text-[10px] text-zinc-400 font-mono mb-1.5 truncate"
      :title="citation.headingPath"
    >
      {{ citation.headingPath }}
    </div>

    <p
      v-if="citation.quote"
      class="text-[11px] text-zinc-500 dark:text-zinc-400 leading-relaxed line-clamp-3 pl-2 border-l-2 border-zinc-200 dark:border-zinc-700"
    >
      {{ citation.quote }}
    </p>

    <div class="flex items-center justify-between mt-2 text-[10px] text-zinc-400">
      <span class="font-mono">chunk #{{ citation.sequence }}</span>
      <span v-if="citation.articleSlug" class="text-emerald-600 dark:text-emerald-400 font-medium">
        回到原文 →
      </span>
      <span v-else class="text-zinc-400">该文档未发布为博客文章</span>
    </div>
  </div>
</template>
