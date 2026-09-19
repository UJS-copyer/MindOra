<script setup lang="ts">
import type { ArticleTocItem, BlogCategory } from '@mindora/types';

defineProps<{
  isDetail: boolean;
  categories: BlogCategory[];
  selectedCategory: string;
  categoryCounts: Record<string, number>;
  totalArticlesCount: number;
  toc: ArticleTocItem[];
  activeHeadingId: string;
  onSelectCategory: (categoryId: string) => void;
  onScrollToHeading: (id: string) => void;
  onBackToFeed: () => void;
}>();
</script>

<template>
  <aside class="w-64 flex-shrink-0 hidden lg:block sticky top-20 h-[calc(100vh-6rem)]">
    <div class="glass-card rounded-xl p-4 flex flex-col h-full overflow-hidden">
      <!-- 模式 A：Feed 浏览模式下的分类知识库树 -->
      <template v-if="!isDetail">
        <div class="flex items-center justify-between pb-3 border-b border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center gap-2">
            <svg class="w-4 h-4 text-emerald-600 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            </svg>
            <span class="font-semibold text-xs text-zinc-900 dark:text-zinc-100 uppercase tracking-wider">知识库体系</span>
          </div>
          <span class="text-[11px] px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-500 font-mono">
            {{ categories.length }} 分类
          </span>
        </div>

        <nav class="mt-3 flex-1 overflow-y-auto space-y-1 pr-1" aria-label="知识库分类">
          <!-- 全部文章节点 -->
          <button
            type="button"
            class="w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs transition-all text-left cursor-pointer"
            :class="!selectedCategory ? 'tree-active font-medium' : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100/70 dark:hover:bg-zinc-800/60'"
            @click="onSelectCategory('')"
          >
            <div class="flex items-center gap-2">
              <svg class="w-3.5 h-3.5 opacity-70" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <rect x="3" y="3" width="7" height="7" />
                <rect x="14" y="3" width="7" height="7" />
                <rect x="14" y="14" width="7" height="7" />
                <rect x="3" y="14" width="7" height="7" />
              </svg>
              <span>全部文章</span>
            </div>
            <span class="text-[11px] opacity-60 font-mono">{{ totalArticlesCount }}</span>
          </button>

          <!-- 动态分类列表 -->
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs transition-all text-left cursor-pointer"
            :class="selectedCategory === category.id ? 'tree-active font-medium' : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100/70 dark:hover:bg-zinc-800/60'"
            @click="onSelectCategory(category.id)"
          >
            <div class="flex items-center gap-2 truncate">
              <svg class="w-3.5 h-3.5 opacity-70 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
              </svg>
              <span class="truncate">{{ category.name }}</span>
            </div>
            <span class="text-[11px] opacity-60 font-mono ml-2">
              {{ categoryCounts[category.id] ?? 0 }}
            </span>
          </button>
        </nav>
      </template>

      <!-- 模式 B：深度阅读模式下的文章 TOC 章节大纲树 -->
      <template v-else>
        <div class="flex items-center justify-between pb-3 border-b border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center gap-2">
            <svg class="w-4 h-4 text-emerald-600 dark:text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <line x1="8" y1="6" x2="21" y2="6" />
              <line x1="8" y1="12" x2="21" y2="12" />
              <line x1="8" y1="18" x2="21" y2="18" />
              <line x1="3" y1="6" x2="3.01" y2="6" />
              <line x1="3" y1="12" x2="3.01" y2="12" />
              <line x1="3" y1="18" x2="3.01" y2="18" />
            </svg>
            <span class="font-semibold text-xs text-zinc-900 dark:text-zinc-100 uppercase tracking-wider">文章目录大纲</span>
          </div>
          <button
            type="button"
            class="text-[11px] px-1.5 py-0.5 rounded text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10 transition-colors cursor-pointer"
            @click="onBackToFeed"
          >
            ← 知识树
          </button>
        </div>

        <nav v-if="toc.length" class="mt-3 flex-1 overflow-y-auto space-y-0.5 pr-1" aria-label="文章目录大纲">
          <button
            v-for="item in toc"
            :key="item.id"
            type="button"
            class="w-full text-left py-1.5 px-2 rounded-md text-xs transition-all flex items-start gap-1.5 cursor-pointer"
            :class="[
              activeHeadingId === item.id
                ? 'tree-active font-medium'
                : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100/60 dark:hover:bg-zinc-800/40',
              item.level === 2 ? 'pl-4' : '',
              item.level === 3 ? 'pl-7 text-[11px]' : ''
            ]"
            @click="onScrollToHeading(item.id)"
          >
            <span class="truncate block w-full">{{ item.title }}</span>
          </button>
        </nav>

        <div v-else class="mt-8 text-center text-zinc-400 text-xs py-8">
          <p>当前文章暂无章节大纲</p>
          <button
            type="button"
            class="mt-3 px-2.5 py-1 rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 hover:bg-zinc-200 transition-colors"
            @click="onBackToFeed"
          >
            返回分类浏览
          </button>
        </div>
      </template>
    </div>
  </aside>
</template>
