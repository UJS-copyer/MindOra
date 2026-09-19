<script setup lang="ts">
import { createApiClient } from '@mindora/api-client';
import type { BlogArticle, BlogCategory, BlogTag } from '@mindora/types';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import { useArticleToc } from './composables/useArticleToc';
import { useReadingHistory } from './composables/useReadingHistory';
import { useWallpaper } from './composables/useWallpaper';

import ArticleFeed from './components/ArticleFeed.vue';
import ArticleReader from './components/ArticleReader.vue';
import CategoryTree from './components/CategoryTree.vue';
import SearchModal from './components/SearchModal.vue';
import SideWidgets from './components/SideWidgets.vue';
import SiteHeader from './components/SiteHeader.vue';

const api = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
});

const path = ref(window.location.pathname);
const articles = ref<BlogArticle[]>([]);
const allArticles = ref<BlogArticle[]>([]);
const categories = ref<BlogCategory[]>([]);
const tags = ref<BlogTag[]>([]);
const selectedCategory = ref('');
const selectedTag = ref('');
const currentArticle = ref<BlogArticle | null>(null);
const loading = ref(true);
const error = ref('');
const isSearchOpen = ref(false);

const { layerClass, layerStyle } = useWallpaper();
const { history, recordArticle, updateProgress } = useReadingHistory();
const { activeId, initScrollSpy, parseMarkdown, scrollToHeading, toc } = useArticleToc();

const isDetail = computed(() => path.value.startsWith('/blog/') && path.value !== '/blog/');
const detailSlug = computed(() =>
  isDetail.value ? decodeURIComponent(path.value.slice('/blog/'.length)) : ''
);

// 计算各分类下的文章计数
const categoryCounts = computed(() => {
  const counts: Record<string, number> = {};
  for (const article of allArticles.value) {
    if (article.categoryId) {
      counts[article.categoryId] = (counts[article.categoryId] ?? 0) + 1;
    }
  }
  return counts;
});

// 计算上一篇与下一篇
const prevArticle = computed(() => {
  if (!currentArticle.value || !articles.value.length) return null;
  const idx = articles.value.findIndex((a) => a.id === currentArticle.value?.id);
  return idx > 0 ? articles.value[idx - 1] : null;
});

const nextArticle = computed(() => {
  if (!currentArticle.value || !articles.value.length) return null;
  const idx = articles.value.findIndex((a) => a.id === currentArticle.value?.id);
  return idx >= 0 && idx < articles.value.length - 1 ? articles.value[idx + 1] : null;
});

// 语义推荐文章（过滤当前文章）
const recommendations = computed(() => {
  return allArticles.value
    .filter((a) => a.slug !== currentArticle.value?.slug)
    .slice(0, 4);
});

async function loadSite() {
  loading.value = true;
  error.value = '';
  try {
    const [articleResponse, categoryResponse, tagResponse, allArticlesResponse] = await Promise.all([
      api.listPublicArticles({
        categoryId: selectedCategory.value || undefined,
        tagId: selectedTag.value || undefined
      }),
      api.listPublicCategories(),
      api.listPublicTags(),
      api.listPublicArticles()
    ]);

    articles.value = articleResponse.data;
    categories.value = categoryResponse.data;
    tags.value = tagResponse.data;
    allArticles.value = allArticlesResponse.data;

    if (isDetail.value && detailSlug.value) {
      const detailRes = await api.getPublicArticle(detailSlug.value);
      currentArticle.value = detailRes.data;

      // 记录阅读历史
      const cat = categories.value.find((c) => c.id === detailRes.data.categoryId);
      recordArticle({
        slug: detailRes.data.slug,
        title: detailRes.data.title,
        categoryName: cat?.name
      });

      // 解析正文大纲并挂载滚动监听
      parseMarkdown(detailRes.data.body);
      void nextTick(() => {
        initScrollSpy();
      });
    } else {
      currentArticle.value = null;
    }
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '未能加载知识库内容。';
  } finally {
    loading.value = false;
  }
}

function navigate(nextPath: string) {
  window.history.pushState({}, '', nextPath);
  path.value = nextPath;
  void loadSite();
}

function handlePopState() {
  path.value = window.location.pathname;
  void loadSite();
}

function selectCategory(categoryId: string) {
  selectedCategory.value = categoryId;
  if (isDetail.value) {
    navigate('/blog');
  } else {
    void loadSite();
  }
}

function selectTag(tagId: string) {
  selectedTag.value = tagId;
  if (isDetail.value) {
    navigate('/blog');
  } else {
    void loadSite();
  }
}

function clearFilters() {
  selectedCategory.value = '';
  selectedTag.value = '';
  void loadSite();
}

function handleSelectArticle(slug: string) {
  navigate(`/blog/${slug}`);
}

function handleProgressUpdate(prog: number) {
  if (currentArticle.value) {
    updateProgress(currentArticle.value.slug, prog);
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    isSearchOpen.value = !isSearchOpen.value;
  }
}

watch(
  () => currentArticle.value?.slug,
  () => {
    if (currentArticle.value) {
      parseMarkdown(currentArticle.value.body);
      void nextTick(() => {
        initScrollSpy();
      });
    }
  }
);

onMounted(() => {
  window.addEventListener('popstate', handlePopState);
  window.addEventListener('keydown', handleKeyDown);
  void loadSite();
});

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState);
  window.removeEventListener('keydown', handleKeyDown);
});
</script>

<template>
  <div class="min-h-screen relative flex flex-col">
    <!-- 环境背景层：支持极光流光、雪山壁纸或微网格 -->
    <div
      id="custom-wallpaper-layer"
      class="fixed inset-0 pointer-events-none z-0 transition-all duration-500"
      :class="layerClass"
      :style="layerStyle"
    ></div>

    <!-- 顶部通透导航栏 -->
    <SiteHeader
      :on-navigate-home="() => navigate('/')"
      :on-navigate-blog="() => navigate('/blog')"
      :on-open-search="() => (isSearchOpen = true)"
    />

    <!-- 主体容器：响应式三栏布局 -->
    <main class="relative z-10 flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6 sm:py-8">
      <!-- 全局错误提示 -->
      <div
        v-if="error"
        class="mb-6 p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 text-xs flex items-center justify-between"
        role="alert"
      >
        <span>{{ error }}</span>
        <button
          type="button"
          class="underline font-medium hover:opacity-80 cursor-pointer"
          @click="loadSite"
        >
          重试
        </button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading && !articles.length" class="flex flex-col items-center justify-center py-24 text-zinc-400 gap-3">
        <div class="w-8 h-8 rounded-full border-2 border-emerald-500/30 border-t-emerald-500 animate-spin"></div>
        <p class="text-xs">正在连接知识中枢并加载内容...</p>
      </div>

      <!-- 三栏主视图 -->
      <div v-else class="flex items-start gap-6">
        <!-- 左侧栏：智能自适应（Feed 模式下为知识库分类树，Reader 模式下为文章 TOC 大纲树） -->
        <CategoryTree
          :is-detail="isDetail"
          :categories="categories"
          :selected-category="selectedCategory"
          :category-counts="categoryCounts"
          :total-articles-count="allArticles.length"
          :toc="toc"
          :active-heading-id="activeId"
          :on-select-category="selectCategory"
          :on-scroll-to-heading="scrollToHeading"
          :on-back-to-feed="() => navigate('/blog')"
        />

        <!-- 中间主内容区 -->
        <div class="flex-1 min-w-0">
          <!-- 浏览模式：文章流 -->
          <ArticleFeed
            v-if="!isDetail"
            :articles="articles"
            :categories="categories"
            :tags="tags"
            :selected-category="selectedCategory"
            :selected-tag="selectedTag"
            :on-select-article="handleSelectArticle"
            :on-select-category="selectCategory"
            :on-select-tag="selectTag"
            :on-clear-filters="clearFilters"
          />

          <!-- 深度阅读模式：文章正文与大纲联动 -->
          <ArticleReader
            v-else-if="currentArticle"
            :article="currentArticle"
            :categories="categories"
            :prev-article="prevArticle"
            :next-article="nextArticle"
            :on-back-to-feed="() => navigate('/blog')"
            :on-update-progress="handleProgressUpdate"
            :on-navigate-article="handleSelectArticle"
          />

          <!-- 找不到文章空状态 -->
          <div v-else class="glass-card rounded-2xl p-12 text-center text-zinc-400">
            <h2 class="text-lg font-semibold text-zinc-700 dark:text-zinc-300 mb-2">文章不存在或已下线</h2>
            <p class="text-xs mb-4">请检查文章链接或返回目录重新浏览。</p>
            <button
              type="button"
              class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-xs font-medium hover:bg-emerald-500 transition-colors cursor-pointer"
              @click="navigate('/blog')"
            >
              返回文章列表
            </button>
          </div>
        </div>

        <!-- 右侧栏：历史阅读进度 + 语义相关推荐 + 知识标签云 -->
        <SideWidgets
          :history="history"
          :recommendations="recommendations"
          :tags="tags"
          :selected-tag="selectedTag"
          :on-select-article="handleSelectArticle"
          :on-select-tag="selectTag"
        />
      </div>
    </main>

    <!-- 全局快捷搜索弹窗 -->
    <SearchModal
      :is-open="isSearchOpen"
      :articles="allArticles"
      :on-close="() => (isSearchOpen = false)"
      :on-select-article="handleSelectArticle"
    />
  </div>
</template>
