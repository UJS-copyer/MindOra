<script setup lang="ts">
import type { BlogArticle, BlogCategory, BlogTag } from '@mindora/types';

defineProps<{
  articles: BlogArticle[];
  categories: BlogCategory[];
  tags: BlogTag[];
  onNavigate: (path: string) => void;
  onSelectArticle: (slug: string) => void;
}>();

function formatDate(value?: string) {
  if (!value) return '';
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' }).format(
    new Date(value)
  );
}

function estimateReadingTime(body = ''): string {
  const charCount = body.length;
  const minutes = Math.max(1, Math.round(charCount / 350));
  return `约 ${minutes} 分钟`;
}
</script>

<template>
  <div class="space-y-10 pb-12">
    <!-- 首页 Hero 介绍区 -->
    <section class="glass-card rounded-2xl p-8 sm:p-12 relative overflow-hidden">
      <div class="max-w-3xl">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-xs font-semibold uppercase tracking-wider mb-4">
          <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
          Personal Knowledge & RAG Engine
        </div>

        <h1 class="text-3xl sm:text-5xl font-bold text-zinc-900 dark:text-zinc-50 tracking-tight leading-tight mb-4">
          记录值得反复阅读的想法，构建个人数字第二大脑。
        </h1>

        <p class="text-base sm:text-lg text-zinc-600 dark:text-zinc-300 leading-relaxed mb-8">
          MindOra 是一个面向深度思考者的全栈知识管理与 RAG 智能中枢。以内容创作为入口，以结构化向量切片与混合检索为基石，连接技术笔记与 AI 智能对话。
        </p>

        <div class="flex flex-wrap items-center gap-4">
          <button
            type="button"
            class="px-6 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-sm shadow-lg shadow-emerald-600/20 transition-all flex items-center gap-2 cursor-pointer"
            @click="onNavigate('/blog')"
          >
            浏览公开博客
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M5 12h14" />
              <path d="m12 5 7 7-7 7" />
            </svg>
          </button>

          <button
            type="button"
            class="px-6 py-3 rounded-xl glass-card hover:border-emerald-500/40 text-zinc-800 dark:text-zinc-200 font-medium text-sm transition-all flex items-center gap-2 cursor-pointer"
            @click="onNavigate('/chat')"
          >
            <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            与 AI 知识库对话
          </button>
        </div>
      </div>
    </section>

    <!-- 4 大核心模块导航磁贴 -->
    <section class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <!-- 磁贴 1：知识库 -->
      <div
        class="glass-card card-hover p-6 rounded-xl cursor-pointer group"
        @click="onNavigate('/knowledge')"
      >
        <div class="w-10 h-10 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
          </svg>
        </div>
        <h3 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 mb-1 group-hover:text-emerald-600 transition-colors">
          结构化知识库
        </h3>
        <p class="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed mb-3">
          按分类体系树纳管的系统笔记与技术沉淀。
        </p>
        <div class="text-xs font-medium text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
          进入知识库 →
        </div>
      </div>

      <!-- 磁贴 2：博客文章 -->
      <div
        class="glass-card card-hover p-6 rounded-xl cursor-pointer group"
        @click="onNavigate('/blog')"
      >
        <div class="w-10 h-10 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
          </svg>
        </div>
        <h3 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 mb-1 group-hover:text-blue-600 transition-colors">
          博客长文
        </h3>
        <p class="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed mb-3">
          系统性技术长文、深度思考与架构设计复盘。
        </p>
        <div class="text-xs font-medium text-blue-600 dark:text-blue-400 flex items-center gap-1">
          阅读博客 →
        </div>
      </div>

      <!-- 磁贴 3：AI 智能问答 -->
      <div
        class="glass-card card-hover p-6 rounded-xl cursor-pointer group"
        @click="onNavigate('/chat')"
      >
        <div class="w-10 h-10 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </div>
        <h3 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 mb-1 group-hover:text-purple-600 transition-colors">
          AI 智能问答
        </h3>
        <p class="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed mb-3">
          基于全站知识库与 RAG 混合检索的交互式问答。
        </p>
        <div class="text-xs font-medium text-purple-600 dark:text-purple-400 flex items-center gap-1">
          开始提问 →
        </div>
      </div>

      <!-- 磁贴 4：全站混合搜索 -->
      <div
        class="glass-card card-hover p-6 rounded-xl cursor-pointer group"
        @click="onNavigate('/blog')"
      >
        <div class="w-10 h-10 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
        </div>
        <h3 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 mb-1 group-hover:text-amber-600 transition-colors">
          全站语义检索
        </h3>
        <p class="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed mb-3">
          支持 ⌘K 快速召唤关键词与 Qdrant 向量检索。
        </p>
        <div class="text-xs font-medium text-amber-600 dark:text-amber-400 flex items-center gap-1">
          按 ⌘K 唤起 →
        </div>
      </div>
    </section>

    <!-- 最新精选文章展示区 -->
    <section class="space-y-4">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-xl font-bold text-zinc-900 dark:text-zinc-50 tracking-tight">最新发布的想法与笔记</h2>
          <p class="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">持续更新的技术探索与研究沉淀</p>
        </div>
        <button
          type="button"
          class="text-xs font-medium text-emerald-600 dark:text-emerald-400 hover:underline cursor-pointer"
          @click="onNavigate('/blog')"
        >
          查看全部文章 ({{ articles.length }}) →
        </button>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <article
          v-for="article in articles.slice(0, 4)"
          :key="article.id"
          class="glass-card card-hover p-6 rounded-xl cursor-pointer group"
          @click="onSelectArticle(article.slug)"
        >
          <div class="flex items-center gap-2 text-xs text-zinc-400 mb-2.5">
            <span class="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-medium text-[11px]">
              {{ categories.find((c) => c.id === article.categoryId)?.name || '技术笔记' }}
            </span>
            <span>·</span>
            <span>{{ formatDate(article.publishedAt) }}</span>
            <span>·</span>
            <span>{{ estimateReadingTime(article.body) }}</span>
          </div>

          <h3 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors mb-2 leading-snug">
            {{ article.title }}
          </h3>

          <p class="text-xs text-zinc-500 dark:text-zinc-400 line-clamp-2 leading-relaxed mb-4">
            {{ article.summary || 'MindOra 知识工坊笔记，点击进入沉浸式阅读。' }}
          </p>

          <div class="text-xs font-medium text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
            阅读全文 →
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
