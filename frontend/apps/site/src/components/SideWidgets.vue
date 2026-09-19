<script setup lang="ts">
import type { BlogArticle, BlogTag, ReadingHistoryItem } from '@mindora/types';

defineProps<{
  history: ReadingHistoryItem[];
  recommendations: BlogArticle[];
  tags: BlogTag[];
  selectedTag: string;
  onSelectArticle: (slug: string) => void;
  onSelectTag: (tagId: string) => void;
}>();
</script>

<template>
  <aside class="w-80 flex-shrink-0 hidden xl:flex flex-col gap-5 sticky top-20 h-[calc(100vh-6rem)] overflow-y-auto pr-1">
    <!-- 模块 1：历史阅读进度 -->
    <div class="glass-card rounded-xl p-4">
      <div class="flex items-center justify-between pb-2.5 mb-3 border-b border-zinc-200/50 dark:border-zinc-800">
        <div class="flex items-center gap-2">
          <svg class="w-4 h-4 text-emerald-600 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          <span class="font-semibold text-xs text-zinc-900 dark:text-zinc-100 uppercase tracking-wider">最近阅读进度</span>
        </div>
        <span class="text-[11px] text-zinc-400 font-mono">{{ history.length }} 篇</span>
      </div>

      <div v-if="history.length" class="space-y-3">
        <div
          v-for="item in history"
          :key="item.slug"
          class="group p-2.5 rounded-lg hover:bg-zinc-100/60 dark:hover:bg-zinc-800/50 transition-colors cursor-pointer"
          role="button"
          tabindex="0"
          @click="onSelectArticle(item.slug)"
          @keydown.enter="onSelectArticle(item.slug)"
        >
          <div class="flex items-center justify-between text-xs mb-1.5">
            <span class="font-medium text-zinc-800 dark:text-zinc-200 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors truncate max-w-[190px]">
              {{ item.title }}
            </span>
            <span class="text-[11px] font-mono font-semibold" :class="item.progress >= 90 ? 'text-emerald-500' : 'text-zinc-400'">
              {{ item.progress }}%
            </span>
          </div>
          <!-- 进度微条 -->
          <div class="w-full bg-zinc-200/60 dark:bg-zinc-800 h-1 rounded-full overflow-hidden">
            <div
              class="h-full bg-emerald-500 rounded-full transition-all duration-300"
              :style="{ width: `${item.progress}%` }"
            ></div>
          </div>
        </div>
      </div>

      <div v-else class="text-center py-6 text-zinc-400 text-xs">
        <p>暂无阅读记录，点击任意文章开始阅读</p>
      </div>
    </div>

    <!-- 模块 2：语义相关推荐 (Qdrant 向量召回) -->
    <div class="glass-card rounded-xl p-4">
      <div class="flex items-center justify-between pb-2.5 mb-3 border-b border-zinc-200/50 dark:border-zinc-800">
        <div class="flex items-center gap-2">
          <svg class="w-4 h-4 text-emerald-600 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
          <span class="font-semibold text-xs text-zinc-900 dark:text-zinc-100 uppercase tracking-wider">语义相关推荐</span>
        </div>
        <span class="text-[11px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-600 font-mono text-[10px]">Qdrant Top-K</span>
      </div>

      <div v-if="recommendations.length" class="space-y-2.5">
        <article
          v-for="rec in recommendations.slice(0, 4)"
          :key="rec.id"
          class="p-2.5 rounded-lg hover:bg-zinc-100/60 dark:hover:bg-zinc-800/50 transition-colors cursor-pointer group"
          @click="onSelectArticle(rec.slug)"
        >
          <h4 class="text-xs font-medium text-zinc-800 dark:text-zinc-200 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors leading-snug line-clamp-2">
            {{ rec.title }}
          </h4>
          <div class="flex items-center gap-2 mt-1.5 text-[11px] text-zinc-400">
            <span>{{ rec.readCount }} 阅读</span>
            <span>·</span>
            <span class="text-emerald-600 dark:text-emerald-400">相关度 92%</span>
          </div>
        </article>
      </div>

      <div v-else class="text-center py-6 text-zinc-400 text-xs">
        <p>暂无相关推荐</p>
      </div>
    </div>

    <!-- 模块 3：热门知识标签云 -->
    <div class="glass-card rounded-xl p-4">
      <div class="flex items-center justify-between pb-2.5 mb-3 border-b border-zinc-200/50 dark:border-zinc-800">
        <div class="flex items-center gap-2">
          <svg class="w-4 h-4 text-emerald-600 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
            <line x1="7" y1="7" x2="7.01" y2="7" />
          </svg>
          <span class="font-semibold text-xs text-zinc-900 dark:text-zinc-100 uppercase tracking-wider">热门知识标签</span>
        </div>
        <span class="text-[11px] text-zinc-400 font-mono">{{ tags.length }} 标签</span>
      </div>

      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="tag in tags"
          :key="tag.id"
          type="button"
          class="px-2.5 py-1 rounded-md text-xs transition-all cursor-pointer"
          :class="
            selectedTag === tag.id
              ? 'bg-emerald-600 text-white font-medium shadow-xs'
              : 'bg-zinc-100/80 dark:bg-zinc-800/80 text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-200/70'
          "
          @click="onSelectTag(tag.id === selectedTag ? '' : tag.id)"
        >
          #{{ tag.name }}
        </button>
      </div>
    </div>
  </aside>
</template>
