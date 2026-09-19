<script setup lang="ts">
import type { BlogArticle, BlogCategory, BlogTag } from '@mindora/types';

const props = defineProps<{
  articles: BlogArticle[];
  categories: BlogCategory[];
  tags: BlogTag[];
  selectedCategory: string;
  selectedTag: string;
  onSelectArticle: (slug: string) => void;
  onSelectCategory: (categoryId: string) => void;
  onSelectTag: (tagId: string) => void;
  onClearFilters: () => void;
}>();

function formatDate(value?: string) {
  if (!value) return '';
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' }).format(
    new Date(value)
  );
}

function getCategoryName(categoryId?: string) {
  if (!categoryId) return '通用知识';
  const cat = props.categories.find((c) => c.id === categoryId);
  return cat ? cat.name : '通用知识';
}

function getTagName(tagId: string) {
  const tag = props.tags.find((t) => t.id === tagId);
  return tag ? tag.name : tagId;
}

function estimateReadingTime(body = ''): string {
  const charCount = body.length;
  const minutes = Math.max(1, Math.round(charCount / 350));
  const kWords = (charCount / 1000).toFixed(1);
  return `${kWords}k 字 · 约 ${minutes} 分钟`;
}
</script>

<template>
  <div class="space-y-6">
    <!-- 顶部滑动分段药丸切换器 -->
    <div class="p-1 rounded-xl glass-card flex items-center gap-1.5 overflow-x-auto">
      <button
        type="button"
        class="px-4 py-2 rounded-lg text-xs font-medium transition-all flex-shrink-0 cursor-pointer"
        :class="!selectedCategory ? 'bg-white dark:bg-zinc-800 text-emerald-600 dark:text-emerald-400 shadow-sm' : 'text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200'"
        @click="onSelectCategory('')"
      >
        全部知识文档
      </button>
      <button
        v-for="cat in categories"
        :key="cat.id"
        type="button"
        class="px-4 py-2 rounded-lg text-xs font-medium transition-all flex-shrink-0 cursor-pointer"
        :class="selectedCategory === cat.id ? 'bg-white dark:bg-zinc-800 text-emerald-600 dark:text-emerald-400 shadow-sm' : 'text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200'"
        @click="onSelectCategory(cat.id)"
      >
        {{ cat.name }}
      </button>
    </div>

    <!-- 筛选提示条 -->
    <div
      v-if="selectedCategory || selectedTag"
      class="flex items-center justify-between px-4 py-2.5 rounded-lg glass-card text-xs text-zinc-600 dark:text-zinc-400"
    >
      <div class="flex items-center gap-2">
        <span class="text-zinc-400">已激活筛选：</span>
        <span v-if="selectedCategory" class="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-medium">
          分类: {{ getCategoryName(selectedCategory) }}
        </span>
        <span v-if="selectedTag" class="px-2 py-0.5 rounded bg-blue-500/10 text-blue-600 dark:text-blue-400 font-medium">
          标签: {{ getTagName(selectedTag) }}
        </span>
      </div>
      <button
        type="button"
        class="text-xs text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 underline cursor-pointer"
        @click="onClearFilters"
      >
        清除筛选
      </button>
    </div>

    <!-- 文章卡片列表 -->
    <div v-if="articles.length" class="space-y-4">
      <article
        v-for="article in articles"
        :key="article.id"
        class="glass-card card-hover rounded-xl p-6 relative group cursor-pointer transition-all duration-300"
        @click="onSelectArticle(article.slug)"
      >
        <div class="flex items-center justify-between mb-3 text-xs text-zinc-400 dark:text-zinc-500">
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              {{ getCategoryName(article.categoryId) }}
            </span>
            <span>·</span>
            <span>{{ estimateReadingTime(article.body) }}</span>
            <span>·</span>
            <span>{{ article.readCount }} 次阅读</span>
          </div>
          <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
        </div>

        <h3 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors mb-2 leading-snug">
          {{ article.title }}
        </h3>

        <p class="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed mb-4 line-clamp-2">
          {{ article.summary || 'MindOra 知识工坊深度笔记与思考，点击进入沉浸阅读。' }}
        </p>

        <div class="flex items-center justify-between pt-2 border-t border-zinc-100 dark:border-zinc-800/80">
          <div class="flex items-center gap-1.5 flex-wrap">
            <button
              v-for="tagId in article.tagIds || []"
              :key="tagId"
              type="button"
              class="px-2 py-0.5 rounded text-[11px] bg-zinc-100/80 dark:bg-zinc-800/70 text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer"
              @click.stop="onSelectTag(tagId)"
            >
              #{{ getTagName(tagId) }}
            </button>
          </div>

          <span class="text-xs font-medium text-emerald-600 dark:text-emerald-400 flex items-center gap-1 group-hover:translate-x-1 transition-transform">
            阅读全文
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M5 12h14" />
              <path d="m12 5 7 7-7 7" />
            </svg>
          </span>
        </div>
      </article>
    </div>

    <!-- 空状态 -->
    <div v-else class="glass-card rounded-xl p-12 text-center text-zinc-400">
      <svg class="w-10 h-10 mx-auto mb-3 opacity-40" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
        <polyline points="14 2 14 8 20 8" />
        <line x1="16" y1="13" x2="8" y2="13" />
        <line x1="16" y1="17" x2="8" y2="17" />
      </svg>
      <p class="text-sm">未匹配到符合条件的公开文章</p>
      <button
        type="button"
        class="mt-4 px-4 py-1.5 rounded-lg bg-emerald-600 text-white text-xs font-medium hover:bg-emerald-500 transition-colors cursor-pointer"
        @click="onClearFilters"
      >
        查看所有文章
      </button>
    </div>
  </div>
</template>
