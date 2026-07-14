<script setup lang="ts">
import { createApiClient } from '@mindora/api-client';
import type { BlogArticle, BlogCategory, BlogTag } from '@mindora/types';
import { computed, onMounted, onUnmounted, ref } from 'vue';

const api = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
});

const path = ref(window.location.pathname);
const articles = ref<BlogArticle[]>([]);
const categories = ref<BlogCategory[]>([]);
const tags = ref<BlogTag[]>([]);
const selectedCategory = ref('');
const selectedTag = ref('');
const currentArticle = ref<BlogArticle | null>(null);
const loading = ref(true);
const error = ref('');

const isDetail = computed(() => path.value.startsWith('/blog/') && path.value !== '/blog/');
const visibleArticles = computed(() => articles.value.slice(0, 6));
const detailSlug = computed(() =>
  isDetail.value ? decodeURIComponent(path.value.slice('/blog/'.length)) : ''
);

async function loadSite() {
  loading.value = true;
  error.value = '';
  try {
    const [articleResponse, categoryResponse, tagResponse] = await Promise.all([
      api.listPublicArticles({
        categoryId: selectedCategory.value || undefined,
        tagId: selectedTag.value || undefined
      }),
      api.listPublicCategories(),
      api.listPublicTags()
    ]);
    articles.value = articleResponse.data;
    categories.value = categoryResponse.data;
    tags.value = tagResponse.data;
    if (isDetail.value && detailSlug.value) {
      currentArticle.value = (await api.getPublicArticle(detailSlug.value)).data;
    } else {
      currentArticle.value = null;
    }
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : 'Unable to load content.';
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

function resetFilters() {
  selectedCategory.value = '';
  selectedTag.value = '';
  void loadSite();
}

function renderMarkdown(markdown: string) {
  const escaped = markdown
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
  return escaped
    .replace(/^### (.*)$/gm, '<h3>$1</h3>')
    .replace(/^## (.*)$/gm, '<h2>$1</h2>')
    .replace(/^# (.*)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/\n/g, '<br />')
    .replace(/^/, '<p>')
    .replace(/$/, '</p>');
}

function formatDate(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(value))
    : '';
}

onMounted(() => {
  window.addEventListener('popstate', handlePopState);
  void loadSite();
});

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState);
});
</script>

<template>
  <main class="site-shell">
    <header class="site-header">
      <button class="brand" type="button" @click="navigate('/')">
        <span class="brand-mark">M</span>
        <span>MindOra</span>
      </button>
      <nav aria-label="公共导航">
        <button type="button" @click="navigate('/')">首页</button>
        <button type="button" @click="navigate('/blog')">文章</button>
      </nav>
    </header>

    <p v-if="error" class="site-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="loading-line">正在加载内容...</p>

    <template v-else-if="!isDetail">
      <section v-if="path === '/'" class="home-intro" aria-labelledby="home-title">
        <p class="eyebrow">个人知识空间</p>
        <h1 id="home-title">记录值得反复阅读的想法。</h1>
        <p>MindOra 用来整理技术笔记、研究记录，以及持续思考的过程。</p>
        <button type="button" @click="navigate('/blog')">阅读最新文章</button>
      </section>

      <section class="blog-band" aria-labelledby="blog-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">公开文章</p>
            <h2 id="blog-title">{{ path === '/' ? '最新文章' : '全部文章' }}</h2>
          </div>
          <button v-if="path === '/'" class="text-button" type="button" @click="navigate('/blog')">
            查看全部
          </button>
        </div>
        <div v-if="path === '/blog'" class="filters">
          <label>
            分类
            <select v-model="selectedCategory" @change="loadSite">
              <option value="">全部分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>
          <label>
            标签
            <select v-model="selectedTag" @change="loadSite">
              <option value="">全部标签</option>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
          <button class="text-button" type="button" @click="resetFilters">清除筛选</button>
        </div>
        <div class="article-grid">
          <article v-for="article in visibleArticles" :key="article.id" class="article-card">
            <p class="article-meta">
              {{ formatDate(article.publishedAt) }} · {{ article.readCount }} 次阅读
            </p>
            <h3>
              <button type="button" @click="navigate(`/blog/${article.slug}`)">
                {{ article.title }}
              </button>
            </h3>
            <p>{{ article.summary || '来自 MindOra 的一篇文章。' }}</p>
            <button class="text-button" type="button" @click="navigate(`/blog/${article.slug}`)">
              阅读全文
            </button>
          </article>
          <p v-if="!visibleArticles.length" class="empty-state">暂时没有已发布的文章。</p>
        </div>
      </section>
    </template>

    <article v-else-if="currentArticle" class="article-detail">
      <button class="back-button" type="button" @click="navigate('/blog')">返回文章列表</button>
      <p class="eyebrow">文章详情</p>
      <h1>{{ currentArticle.title }}</h1>
      <div class="detail-meta">
        <span>{{ formatDate(currentArticle.publishedAt) }}</span>
        <span>{{ currentArticle.readCount }} 次阅读</span>
      </div>
      <p v-if="currentArticle.summary" class="detail-summary">{{ currentArticle.summary }}</p>
      <div class="markdown-body" v-html="renderMarkdown(currentArticle.body)"></div>
    </article>

    <p v-else class="empty-state">文章不存在或已下线。</p>
  </main>
</template>
