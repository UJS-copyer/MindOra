<script setup lang="ts">
import { createApiClient } from '@mindora/api-client';
import type { ArticleDraftInput, BlogArticle, BlogCategory, BlogTag } from '@mindora/types';
import { computed, onMounted, reactive, ref } from 'vue';
import { MdEditor } from 'md-editor-v3';
import { uploadCoverAsset } from './asset-upload';
import { createEditorState, resetEditorState } from './editor-state';
import 'md-editor-v3/lib/style.css';

type AdminView = 'articles' | 'editor' | 'taxonomy';

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const token = ref(localStorage.getItem('mindora_admin_token') ?? '');
const api = createApiClient({
  baseUrl,
  authToken: () => token.value || undefined
});

const email = ref('');
const password = ref('');
const loginError = ref('');
const busy = ref(false);
const notice = ref('');
const noticeType = ref<'success' | 'error'>('success');
const activeView = ref<AdminView>('articles');
const articles = ref<BlogArticle[]>([]);
const categories = ref<BlogCategory[]>([]);
const tags = ref<BlogTag[]>([]);
const filterStatus = ref('all');
const editorId = ref<string | null>(null);
const newCategory = ref('');
const newTag = ref('');
const taxonomyError = ref('');
const editorState = createEditorState();
const coverFile = ref<globalThis.File | null>(editorState.coverFile);
const coverInput = ref<globalThis.HTMLInputElement | null>(null);

const editor = reactive<ArticleDraftInput>({
  title: '',
  slug: '',
  summary: '',
  body: '',
  coverAssetId: '',
  categoryId: '',
  tagIds: [],
  visibility: 'public'
});

const filteredArticles = computed(() => {
  if (filterStatus.value === 'all') {
    return articles.value;
  }
  return articles.value.filter((article) => article.status === filterStatus.value);
});

const editorTitle = computed(() => (editorId.value ? '编辑文章' : '新建文章'));
const viewTitle = computed(() => {
  if (activeView.value === 'editor') {
    return editorTitle.value;
  }
  return activeView.value === 'taxonomy' ? '分类与标签' : '文章管理';
});

function showSuccess(message: string) {
  noticeType.value = 'success';
  notice.value = message;
}

function showError(error: unknown) {
  noticeType.value = 'error';
  notice.value = getErrorMessage(error);
}

function clearNotice() {
  notice.value = '';
  noticeType.value = 'success';
}

function resetEditor(article?: BlogArticle) {
  editorId.value = article?.id ?? null;
  editor.title = article?.title ?? '';
  editor.slug = article?.slug ?? '';
  editor.summary = article?.summary ?? '';
  editor.body = article?.body ?? '';
  editor.coverAssetId = article?.coverAssetId ?? '';
  editor.categoryId = article?.categoryId ?? '';
  editor.tagIds = article?.tagIds ? [...article.tagIds] : [];
  editor.visibility = article?.visibility ?? 'public';
  editorState.coverInput = coverInput.value;
  resetEditorState(editorState);
  coverFile.value = editorState.coverFile;
  activeView.value = 'editor';
  clearNotice();
}

async function loadAdminData() {
  if (!token.value) {
    return;
  }
  busy.value = true;
  try {
    const [articleResponse, categoryResponse, tagResponse] = await Promise.all([
      api.listAdminArticles(),
      api.listCategories(),
      api.listTags()
    ]);
    articles.value = articleResponse.data;
    categories.value = categoryResponse.data;
    tags.value = tagResponse.data;
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function submitLogin() {
  loginError.value = '';
  if (!email.value.trim() || !password.value) {
    loginError.value = '请输入邮箱和密码。';
    return;
  }
  busy.value = true;
  try {
    const response = await api.login(email.value.trim(), password.value);
    token.value = response.data.accessToken;
    localStorage.setItem('mindora_admin_token', token.value);
    await loadAdminData();
  } catch (error) {
    loginError.value = getErrorMessage(error);
  } finally {
    busy.value = false;
  }
}

function logout() {
  token.value = '';
  localStorage.removeItem('mindora_admin_token');
  activeView.value = 'articles';
  clearNotice();
}

function startNewArticle() {
  resetEditor();
}

function editArticle(article: BlogArticle) {
  if (article.status === 'published') {
    showError('已发布文章需要先下线，再进行修改。');
    return;
  }
  resetEditor(article);
}

function selectCoverFile(event: globalThis.Event) {
  const input = event.target as globalThis.HTMLInputElement;
  coverFile.value = input.files?.[0] ?? null;
  editorState.coverFile = coverFile.value;
  editorState.coverInput = input;
}

function clearCover() {
  editor.coverAssetId = '';
  editorState.coverInput = coverInput.value;
  resetEditorState(editorState);
  coverFile.value = editorState.coverFile;
  showSuccess('封面已移除，保存后生效。');
}

async function uploadCover() {
  if (!coverFile.value) {
    showError('请先选择封面图片。');
    return;
  }
  busy.value = true;
  clearNotice();
  try {
    const asset = await uploadCoverAsset(api, coverFile.value);
    editor.coverAssetId = asset.id;
    showSuccess('封面上传成功。');
    editorState.coverInput = coverInput.value;
    resetEditorState(editorState);
    coverFile.value = editorState.coverFile;
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function saveArticle() {
  busy.value = true;
  clearNotice();
  try {
    if (editorId.value) {
      await api.updateArticle(editorId.value, editor);
      showSuccess('草稿已更新。');
    } else {
      const response = await api.createArticle(editor);
      editorId.value = response.data.id;
      showSuccess('草稿已保存。');
    }
    await loadAdminData();
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function changePublication(article: BlogArticle) {
  busy.value = true;
  clearNotice();
  try {
    if (article.status === 'published') {
      await api.unpublishArticle(article.id);
      showSuccess('文章已下线，可以继续编辑。');
    } else {
      await api.publishArticle(article.id);
      showSuccess('文章已发布。');
    }
    await loadAdminData();
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

async function addCategory() {
  if (!newCategory.value.trim()) {
    return;
  }
  taxonomyError.value = '';
  try {
    const response = await api.createCategory(newCategory.value.trim());
    categories.value.push(response.data);
    newCategory.value = '';
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

async function addTag() {
  if (!newTag.value.trim()) {
    return;
  }
  taxonomyError.value = '';
  try {
    const response = await api.createTag(newTag.value.trim());
    tags.value.push(response.data);
    newTag.value = '';
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    draft: '草稿',
    published: '已发布',
    unpublished: '已下线'
  };
  return labels[status] ?? status;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(value));
}

function getErrorMessage(error: unknown) {
  if (typeof error === 'string') {
    return error;
  }
  if (error instanceof Error) {
    const coded = error as Error & { code?: string; status?: number };
    const messages: Record<string, string> = {
      article_invalid_transition: '已发布文章需要先下线，再进行修改。',
      article_slug_exists: '文章路径已存在，请换一个 slug。',
      article_title_required: '请输入文章标题。',
      article_slug_required: '请输入文章 slug。',
      article_body_required: '请输入正文内容。',
      category_not_found: '选择的分类不存在，请刷新后重试。',
      tag_not_found: '选择的标签不存在，请刷新后重试。',
      invalid_credentials: '邮箱或密码不正确。',
      validation_error: '输入内容不完整，请检查后重试。',
      asset_type_unsupported: '仅支持 PNG、JPG、GIF、WebP 图片。',
      asset_too_large: '封面图片不能超过 5MB。',
      asset_storage_error: '封面保存失败，请稍后重试。'
    };
    if (coded.code && messages[coded.code]) {
      return messages[coded.code];
    }
    if (coded.status === 401 || coded.status === 403) {
      return '登录状态已失效，请重新登录。';
    }
    return error.message || '请求失败，请稍后重试。';
  }
  return '请求失败，请稍后重试。';
}

function cancelEdit() {
  activeView.value = 'articles';
  resetEditorState(editorState);
  coverFile.value = editorState.coverFile;
  clearNotice();
}

onMounted(loadAdminData);
</script>

<template>
  <main class="admin-shell">
    <section v-if="!token" class="login-shell" aria-labelledby="admin-title">
      <p class="eyebrow">MindOra 管理端</p>
      <h1 id="admin-title">登录管理后台</h1>
      <p class="login-copy">管理公开文章、分类和标签。</p>
      <form @submit.prevent="submitLogin">
        <label>
          邮箱
          <input v-model="email" autocomplete="email" name="email" type="email" />
        </label>
        <label>
          密码
          <input
            v-model="password"
            autocomplete="current-password"
            name="password"
            type="password"
          />
        </label>
        <button :disabled="busy" type="submit">{{ busy ? '登录中...' : '登录' }}</button>
      </form>
      <p v-if="loginError" class="status error" role="alert">{{ loginError }}</p>
    </section>

    <div v-else class="admin-layout">
      <aside class="admin-sidebar">
        <div>
          <p class="eyebrow">MindOra</p>
          <h1>内容工作台</h1>
        </div>
        <nav aria-label="管理端导航">
          <button
            :class="{ active: activeView === 'articles' }"
            type="button"
            @click="activeView = 'articles'"
          >
            文章管理
          </button>
          <button
            :class="{ active: activeView === 'taxonomy' }"
            type="button"
            @click="activeView = 'taxonomy'"
          >
            分类与标签
          </button>
        </nav>
        <button class="quiet-button" type="button" @click="logout">退出登录</button>
      </aside>

      <section class="admin-content">
        <header class="content-header">
          <div>
            <p class="eyebrow">内容管理</p>
            <h2>{{ viewTitle }}</h2>
          </div>
          <button v-if="activeView === 'articles'" type="button" @click="startNewArticle">
            新建文章
          </button>
          <button
            v-if="activeView === 'editor'"
            class="quiet-button"
            type="button"
            @click="cancelEdit"
          >
            取消编辑
          </button>
        </header>

        <p v-if="notice" class="notice" :class="`notice-${noticeType}`" role="alert">
          {{ notice }}
        </p>
        <p v-if="busy" class="loading-line">正在处理，请稍候...</p>

        <section
          v-if="activeView === 'articles'"
          class="content-section"
          aria-labelledby="articles-title"
        >
          <div class="toolbar">
            <div>
              <h3 id="articles-title">文章列表</h3>
              <p>共 {{ articles.length }} 篇文章</p>
            </div>
            <label class="compact-field">
              状态
              <select v-model="filterStatus">
                <option value="all">全部</option>
                <option value="draft">草稿</option>
                <option value="published">已发布</option>
                <option value="unpublished">已下线</option>
              </select>
            </label>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>标题</th>
                  <th>状态</th>
                  <th>阅读数</th>
                  <th>更新时间</th>
                  <th><span class="sr-only">操作</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="article in filteredArticles" :key="article.id">
                  <td>
                    <strong>{{ article.title }}</strong>
                    <span class="muted">{{ article.slug }}</span>
                  </td>
                  <td>
                    <span class="status-chip" :data-status="article.status">{{
                      statusLabel(article.status)
                    }}</span>
                  </td>
                  <td>{{ article.readCount }}</td>
                  <td>{{ formatDate(article.updatedAt) }}</td>
                  <td class="row-actions">
                    <button
                      v-if="article.status !== 'published'"
                      type="button"
                      @click="editArticle(article)"
                    >
                      编辑
                    </button>
                    <button type="button" @click="changePublication(article)">
                      {{ article.status === 'published' ? '下线' : '发布' }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!filteredArticles.length">
                  <td class="empty-state" colspan="5">没有符合条件的文章。</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section
          v-else-if="activeView === 'editor'"
          class="editor-section"
          aria-labelledby="editor-title"
        >
          <div class="editor-fields">
            <label>
              标题
              <input v-model="editor.title" type="text" />
            </label>
            <label>
              文章路径
              <input v-model="editor.slug" type="text" />
            </label>
            <label>
              摘要
              <textarea v-model="editor.summary" rows="3"></textarea>
            </label>
            <div class="field-grid">
              <label>
                分类
                <select v-model="editor.categoryId">
                  <option value="">未分类</option>
                  <option v-for="category in categories" :key="category.id" :value="category.id">
                    {{ category.name }}
                  </option>
                </select>
              </label>
              <label>
                可见范围
                <select v-model="editor.visibility">
                  <option value="public">公开</option>
                  <option value="private">私有</option>
                </select>
              </label>
            </div>
            <div v-if="editor.coverAssetId" class="cover-reference">
              <span>当前封面已上传</span>
              <button class="quiet-button" type="button" @click="clearCover">移除封面</button>
            </div>
            <div class="cover-upload">
              <label>
                封面图片
                <input ref="coverInput" accept="image/*" type="file" @change="selectCoverFile" />
              </label>
              <button :disabled="busy || !coverFile" type="button" @click="uploadCover">
                {{ busy ? '上传中...' : '上传封面' }}
              </button>
              <span v-if="coverFile" class="muted">{{ coverFile.name }}</span>
            </div>
            <fieldset>
              <legend>标签</legend>
              <label v-for="tag in tags" :key="tag.id" class="check-label">
                <input v-model="editor.tagIds" :value="tag.id" type="checkbox" />
                {{ tag.name }}
              </label>
              <span v-if="!tags.length" class="muted">请先在“分类与标签”中创建标签。</span>
            </fieldset>
          </div>
          <div class="markdown-field">
            <div class="field-heading">
              <div>
                <h3 id="editor-title">正文内容</h3>
                <p>支持 Markdown，保存后由前台展示。</p>
              </div>
            </div>
            <MdEditor v-model="editor.body" language="en-US" :preview="true" />
            <div class="editor-actions">
              <button class="quiet-button" :disabled="busy" type="button" @click="cancelEdit">
                取消
              </button>
              <button :disabled="busy" type="button" @click="saveArticle">
                {{ busy ? '保存中...' : '保存草稿' }}
              </button>
            </div>
          </div>
        </section>

        <section v-else class="content-section taxonomy-section" aria-labelledby="taxonomy-title">
          <div class="toolbar">
            <div>
              <h3 id="taxonomy-title">分类与标签</h3>
              <p>维护文章使用的分类和标签。</p>
            </div>
          </div>
          <p v-if="taxonomyError" class="status error" role="alert">{{ taxonomyError }}</p>
          <div class="taxonomy-grid">
            <form class="taxonomy-panel" @submit.prevent="addCategory">
              <h3>分类</h3>
              <label>
                新分类名称
                <input v-model="newCategory" type="text" />
              </label>
              <button type="submit">添加分类</button>
              <ul>
                <li v-for="category in categories" :key="category.id">{{ category.name }}</li>
              </ul>
            </form>
            <form class="taxonomy-panel" @submit.prevent="addTag">
              <h3>标签</h3>
              <label>
                新标签名称
                <input v-model="newTag" type="text" />
              </label>
              <button type="submit">添加标签</button>
              <ul>
                <li v-for="tag in tags" :key="tag.id">{{ tag.name }}</li>
              </ul>
            </form>
          </div>
        </section>
      </section>
    </div>
  </main>
</template>
