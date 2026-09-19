<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { BlogArticle } from '@mindora/types';

const props = defineProps<{
  isOpen: boolean;
  articles: BlogArticle[];
  onClose: () => void;
  onSelectArticle: (slug: string) => void;
}>();

const query = ref('');
const inputRef = ref<HTMLInputElement | null>(null);

watch(
  () => props.isOpen,
  (val) => {
    if (val) {
      query.value = '';
      setTimeout(() => inputRef.value?.focus(), 50);
    }
  }
);

const filteredArticles = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return props.articles.slice(0, 5);
  return props.articles.filter(
    (a) => a.title.toLowerCase().includes(q) || (a.summary && a.summary.toLowerCase().includes(q))
  );
});

function handleSelect(slug: string) {
  props.onSelectArticle(slug);
  props.onClose();
}
</script>

<template>
  <div
    v-if="isOpen"
    class="fixed inset-0 z-[100] flex items-start justify-center pt-24 px-4 bg-black/40 backdrop-blur-sm"
    @click.self="onClose"
  >
    <div
      class="glass-card w-full max-w-xl rounded-2xl p-4 shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-200"
    >
      <div class="flex items-center gap-3 px-3 py-2 border-b border-zinc-200/60 dark:border-zinc-800">
        <svg class="w-4 h-4 text-zinc-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          ref="inputRef"
          v-model="query"
          type="text"
          placeholder="搜索知识库文章、概念、关键词..."
          class="flex-1 bg-transparent border-0 outline-none text-sm text-zinc-900 dark:text-zinc-100 placeholder-zinc-400"
          @keydown.esc="onClose"
        />
        <kbd class="text-[11px] px-1.5 py-0.5 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-500 font-mono">ESC</kbd>
      </div>

      <div class="mt-3 max-h-80 overflow-y-auto space-y-1">
        <div
          v-for="article in filteredArticles"
          :key="article.id"
          class="p-3 rounded-xl hover:bg-zinc-100/70 dark:hover:bg-zinc-800/60 transition-colors cursor-pointer group"
          role="button"
          tabindex="0"
          @click="handleSelect(article.slug)"
          @keydown.enter="handleSelect(article.slug)"
        >
          <div class="text-xs font-semibold text-zinc-900 dark:text-zinc-100 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors mb-1">
            {{ article.title }}
          </div>
          <div class="text-[11px] text-zinc-400 line-clamp-1">
            {{ article.summary || 'MindOra 知识笔记' }}
          </div>
        </div>

        <div v-if="!filteredArticles.length" class="text-center py-8 text-xs text-zinc-400">
          未检索到相关内容
        </div>
      </div>
    </div>
  </div>
</template>
