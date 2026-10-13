<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { BlogArticle, BlogCategory } from '@mindora/types';
import { anchorIdFromText } from '../utils/anchor';

const props = defineProps<{
  article: BlogArticle;
  categories: BlogCategory[];
  onBackToFeed: () => void;
  onUpdateProgress: (progress: number) => void;
  onNavigateArticle: (slug: string) => void;
  prevArticle?: BlogArticle | null;
  nextArticle?: BlogArticle | null;
  /** 由引用回链带过来的目标章节锚点（`anchorIdFromHeadingPath` 的结果）。 */
  anchorId?: string | null;
}>();

const readingProgress = ref(0);

function formatDate(value?: string) {
  if (!value) return '';
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' }).format(
    new Date(value)
  );
}

const categoryName = computed(() => {
  if (!props.article.categoryId) return '技术笔记';
  const cat = props.categories.find((c) => c.id === props.article.categoryId);
  return cat ? cat.name : '技术笔记';
});

function estimateReadingTime(body = ''): string {
  const charCount = body.length;
  const minutes = Math.max(1, Math.round(charCount / 350));
  return `约 ${minutes} 分钟阅读`;
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function renderInlineMarkdown(value: string) {
  return escapeHtml(value)
    .replace(/!\[([^\]]*)]\(([^)]+)\)/g, '<img src="$2" alt="$1" class="rounded-xl shadow-lg my-4" />')
    .replace(/\[([^\]]+)]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer" class="text-emerald-600 hover:underline">$1</a>')
    .replace(/`([^`]+)`/g, '<code class="px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-800 text-emerald-600 dark:text-emerald-400 font-mono text-xs">$1</code>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>');
}

function renderMarkdown(markdown: string) {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const blocks: string[] = [];
  let paragraph: string[] = [];
  let listType: 'ul' | 'ol' | null = null;
  let codeLines: string[] = [];
  let codeLang = '';
  let inCode = false;

  const flushParagraph = () => {
    if (!paragraph.length) return;
    blocks.push(`<p class="leading-relaxed mb-4 text-zinc-700 dark:text-zinc-300">${renderInlineMarkdown(paragraph.join(' '))}</p>`);
    paragraph = [];
  };

  const closeList = () => {
    if (!listType) return;
    blocks.push(`</${listType}>`);
    listType = null;
  };

  for (const line of lines) {
    const codeMatch = line.match(/^\s*```(\w*)/);
    if (codeMatch) {
      if (inCode) {
        blocks.push(
          `<div class="relative group my-5 rounded-xl overflow-hidden border border-zinc-200 dark:border-zinc-800 bg-[#121216]">
            <div class="flex items-center justify-between px-4 py-2 bg-zinc-900/60 border-b border-zinc-800/80 text-xs text-zinc-400">
              <span class="font-mono text-[11px]">${codeLang || 'CODE'}</span>
              <button type="button" class="copy-code-btn px-2 py-0.5 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-[11px] transition-colors cursor-pointer">复制</button>
            </div>
            <pre class="p-4 overflow-x-auto font-mono text-xs text-zinc-200 leading-relaxed"><code>${escapeHtml(codeLines.join('\n'))}</code></pre>
          </div>`
        );
        codeLines = [];
        codeLang = '';
        inCode = false;
      } else {
        flushParagraph();
        closeList();
        inCode = true;
        codeLang = codeMatch[1] || '';
      }
      continue;
    }

    if (inCode) {
      codeLines.push(line);
      continue;
    }

    if (!line.trim()) {
      flushParagraph();
      closeList();
      continue;
    }

    const heading = line.match(/^\s{0,3}(#{1,3})\s+(.+)$/);
    if (heading) {
      flushParagraph();
      closeList();
      const level = heading[1].length;
      const rawTitle = heading[2].trim();
      const title = renderInlineMarkdown(rawTitle);
      const slug = anchorIdFromText(rawTitle);

      if (level === 1) {
        blocks.push(`<h1 id="${slug}" class="text-2xl font-bold mt-8 mb-4 text-zinc-900 dark:text-zinc-50 tracking-tight scroll-mt-20">${title}</h1>`);
      } else if (level === 2) {
        blocks.push(`<h2 id="${slug}" class="text-xl font-bold mt-7 mb-3 pb-2 border-b border-zinc-200/60 dark:border-zinc-800 text-zinc-900 dark:text-zinc-50 tracking-tight scroll-mt-20">${title}</h2>`);
      } else {
        blocks.push(`<h3 id="${slug}" class="text-base font-semibold mt-6 mb-2 text-zinc-900 dark:text-zinc-100 scroll-mt-20">${title}</h3>`);
      }
      continue;
    }

    const unordered = line.match(/^\s{0,3}[-*+]\s+(.+)$/);
    const ordered = line.match(/^\s{0,3}\d+[.)]\s+(.+)$/);
    if (unordered || ordered) {
      flushParagraph();
      const nextType = unordered ? 'ul' : 'ol';
      if (listType !== nextType) {
        closeList();
        listType = nextType;
        blocks.push(`<${listType} class="list-disc pl-5 my-4 space-y-1 text-zinc-700 dark:text-zinc-300">`);
      }
      blocks.push(`<li>${renderInlineMarkdown((unordered || ordered)?.[1] || '')}</li>`);
      continue;
    }

    const quote = line.match(/^\s{0,3}>\s?(.+)$/);
    if (quote) {
      flushParagraph();
      closeList();
      blocks.push(
        `<blockquote class="border-l-4 border-emerald-500 bg-emerald-500/5 dark:bg-emerald-500/10 px-4 py-2.5 my-4 rounded-r-lg text-xs italic text-zinc-700 dark:text-zinc-300">
          ${renderInlineMarkdown(quote[1])}
        </blockquote>`
      );
      continue;
    }

    paragraph.push(line.trim());
  }

  if (inCode) {
    blocks.push(
      `<pre class="p-4 overflow-x-auto rounded-xl bg-zinc-900 text-zinc-200 font-mono text-xs my-4"><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`
    );
  }
  flushParagraph();
  closeList();
  return blocks.join('\n');
}

const renderedHtml = computed(() => renderMarkdown(props.article.body));

function handleScroll() {
  const scrollTop = window.scrollY || document.documentElement.scrollTop;
  const docHeight = document.documentElement.scrollHeight - window.innerHeight;
  if (docHeight > 0) {
    const progress = Math.min(100, Math.max(0, (scrollTop / docHeight) * 100));
    readingProgress.value = Math.round(progress);
    props.onUpdateProgress(readingProgress.value);
  }
}

function attachCodeCopyButtons() {
  const buttons = document.querySelectorAll<HTMLElement>('.copy-code-btn');
  buttons.forEach((btn) => {
    btn.onclick = () => {
      const codeBlock = btn.closest('.relative')?.querySelector('code');
      if (codeBlock) {
        void navigator.clipboard.writeText(codeBlock.innerText);
        btn.innerText = '已复制!';
        btn.classList.add('bg-emerald-600', 'text-white');
        setTimeout(() => {
          btn.innerText = '复制';
          btn.classList.remove('bg-emerald-600', 'text-white');
        }, 1800);
      }
    };
  });
}

/** 引用回链：滚动到引文所在章节；找不到对应标题时静默停在文首（不回退成报错）。 */
function scrollToAnchor(anchorId: string | null | undefined) {
  if (!anchorId) return;
  void nextTick(() => {
    const target = document.getElementById(anchorId);
    if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}

watch(
  () => props.article.slug,
  () => {
    window.scrollTo({ top: 0, behavior: 'instant' });
    readingProgress.value = 0;
    void nextTick(() => {
      attachCodeCopyButtons();
    });
  }
);

// 同一篇文章内重复点引用时 slug 不变，靠 anchorId 变化触发定位。
watch(() => [props.article.slug, props.anchorId], () => {
  scrollToAnchor(props.anchorId);
});

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true });
  void nextTick(() => {
    attachCodeCopyButtons();
  });
  scrollToAnchor(props.anchorId);
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <!-- 顶部细微阅读进度条 -->
  <div class="fixed top-0 left-0 right-0 h-0.5 z-[60] bg-transparent">
    <div
      class="h-full bg-gradient-to-r from-emerald-500 to-teal-400 transition-all duration-150 ease-out"
      :style="{ width: `${readingProgress}%` }"
    ></div>
  </div>

  <article class="glass-card rounded-2xl p-6 sm:p-10 relative overflow-hidden">
    <!-- 面包屑与返回列表导航 -->
    <div class="flex items-center justify-between pb-6 mb-6 border-b border-zinc-200/60 dark:border-zinc-800">
      <div class="flex items-center gap-2 text-xs text-zinc-500">
        <button
          type="button"
          class="hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors cursor-pointer"
          @click="onBackToFeed"
        >
          知识库
        </button>
        <span>/</span>
        <span class="text-zinc-400">{{ categoryName }}</span>
        <span>/</span>
        <span class="text-zinc-800 dark:text-zinc-200 truncate max-w-[200px]">{{ article.title }}</span>
      </div>

      <button
        type="button"
        class="text-xs px-3 py-1.5 rounded-lg glass-card text-zinc-600 dark:text-zinc-300 hover:text-emerald-600 dark:hover:text-emerald-400 flex items-center gap-1.5 transition-colors cursor-pointer"
        @click="onBackToFeed"
      >
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <line x1="19" y1="12" x2="5" y2="12" />
          <polyline points="12 19 5 12 12 5" />
        </svg>
        返回文章列表
      </button>
    </div>

    <!-- 文章头信息 -->
    <header class="mb-8">
      <div class="flex flex-wrap items-center gap-2.5 mb-3 text-xs">
        <span class="px-2.5 py-0.5 rounded-md font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
          {{ categoryName }}
        </span>
        <span class="text-zinc-400">·</span>
        <span class="text-zinc-500">{{ formatDate(article.publishedAt) }}</span>
        <span class="text-zinc-400">·</span>
        <span class="text-zinc-500">{{ estimateReadingTime(article.body) }}</span>
        <span class="text-zinc-400">·</span>
        <span class="text-zinc-500">{{ article.readCount }} 次阅读</span>

        <!-- 知识库入库标识 -->
        <span
          v-if="article.knowledgeEnabled"
          class="ml-auto inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
        >
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
          已入知识库 · Qdrant 索引就绪
        </span>
      </div>

      <h1 class="text-2xl sm:text-3xl font-bold text-zinc-900 dark:text-zinc-50 tracking-tight leading-snug">
        {{ article.title }}
      </h1>

      <!-- 摘要高亮卡片 -->
      <div
        v-if="article.summary"
        class="mt-5 p-4 rounded-xl bg-zinc-100/70 dark:bg-zinc-800/50 border-l-4 border-emerald-500 text-xs text-zinc-600 dark:text-zinc-300 leading-relaxed"
      >
        <div class="font-semibold text-zinc-800 dark:text-zinc-200 mb-1 flex items-center gap-1.5">
          <svg class="w-3.5 h-3.5 text-emerald-500" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          核心摘要
        </div>
        <p>{{ article.summary }}</p>
      </div>
    </header>

    <!-- Markdown 正文 -->
    <div class="prose-content markdown-body max-w-none" v-html="renderedHtml"></div>

    <!-- 底部导航与作者签名 -->
    <footer class="mt-12 pt-6 border-t border-zinc-200/60 dark:border-zinc-800 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-zinc-500">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-full bg-zinc-200 dark:bg-zinc-700 flex items-center justify-center font-bold text-zinc-600 dark:text-zinc-300">
          MO
        </div>
        <div>
          <div class="font-medium text-zinc-800 dark:text-zinc-200">MindOra 知识中枢</div>
          <div class="text-[11px] text-zinc-400">构建个人数字第二大脑 · 深度知识检索</div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <button
          v-if="prevArticle"
          type="button"
          class="px-3 py-1.5 rounded-lg glass-card hover:text-emerald-600 transition-colors cursor-pointer"
          @click="onNavigateArticle(prevArticle.slug)"
        >
          ← 上一篇: {{ prevArticle.title.slice(0, 10) }}...
        </button>
        <button
          v-if="nextArticle"
          type="button"
          class="px-3 py-1.5 rounded-lg glass-card hover:text-emerald-600 transition-colors cursor-pointer"
          @click="onNavigateArticle(nextArticle.slug)"
        >
          下一篇: {{ nextArticle.title.slice(0, 10) }}... →
        </button>
      </div>
    </footer>
  </article>
</template>
