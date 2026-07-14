<script setup lang="ts">
import { createApiClient } from '@mindora/api-client';
import type { ArticleDraftInput, Asset, BlogArticle, BlogCategory, BlogTag } from '@mindora/types';
import { computed, onMounted, reactive, ref } from 'vue';
import { MdEditor } from 'md-editor-v3';
import { uploadCoverAsset } from './asset-upload';
import { createEditorState, resetEditorState } from './editor-state';
import 'md-editor-v3/lib/style.css';

type AdminView = 'articles' | 'editor' | 'taxonomy' | 'assets';

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
const assets = ref<Asset[]>([]);
const filterStatus = ref('all');
const editorId = ref<string | null>(null);
const newCategory = ref('');
const newTag = ref('');
const taxonomyError = ref('');
const editingCategoryId = ref<string | null>(null);
const editingCategoryName = ref('');
const editingTagId = ref<string | null>(null);
const editingTagName = ref('');
const assetUploadFile = ref<globalThis.File | null>(null);
const assetInput = ref<globalThis.HTMLInputElement | null>(null);
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
  if (activeView.value === 'taxonomy') {
    return '分类与标签';
  }
  return activeView.value === 'assets' ? '资产管理' : '文章管理';
});

const selectedCoverAsset = computed(() =>
  editor.coverAssetId ? assets.value.find((asset) => asset.id === editor.coverAssetId) : undefined
);

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
    const [articleResponse, categoryResponse, tagResponse, assetResponse] = await Promise.all([
      api.listAdminArticles(),
      api.listCategories(),
      api.listTags(),
      api.listAssets()
    ]);
    articles.value = articleResponse.data;
    categories.value = categoryResponse.data;
    tags.value = tagResponse.data;
    assets.value = assetResponse.data;
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
    assets.value = [asset, ...assets.value.filter((item) => item.id !== asset.id)];
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

function selectExistingCover(asset: Asset) {
  editor.coverAssetId = asset.id;
  showSuccess('已选择现有资产作为封面，保存文章后生效。');
}

function useAssetAsCover(asset: Asset) {
  selectExistingCover(asset);
  activeView.value = 'editor';
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
    showSuccess('分类已添加。');
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

function startEditCategory(category: BlogCategory) {
  editingCategoryId.value = category.id;
  editingCategoryName.value = category.name;
}

function cancelEditCategory() {
  editingCategoryId.value = null;
  editingCategoryName.value = '';
}

async function saveCategory(category: BlogCategory) {
  if (!editingCategoryName.value.trim()) {
    taxonomyError.value = '请输入分类名称。';
    return;
  }
  taxonomyError.value = '';
  try {
    const response = await api.updateCategory(category.id, editingCategoryName.value.trim());
    categories.value = categories.value.map((item) =>
      item.id === category.id ? response.data : item
    );
    cancelEditCategory();
    showSuccess('分类已更新。');
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

async function removeCategory(category: BlogCategory) {
  if (!globalThis.confirm(`确定停用分类“${category.name}”？`)) {
    return;
  }
  taxonomyError.value = '';
  try {
    await api.deleteCategory(category.id);
    categories.value = categories.value.filter((item) => item.id !== category.id);
    if (editor.categoryId === category.id) {
      editor.categoryId = '';
    }
    showSuccess('分类已停用。');
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
    showSuccess('标签已添加。');
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

function startEditTag(tag: BlogTag) {
  editingTagId.value = tag.id;
  editingTagName.value = tag.name;
}

function cancelEditTag() {
  editingTagId.value = null;
  editingTagName.value = '';
}

async function saveTag(tag: BlogTag) {
  if (!editingTagName.value.trim()) {
    taxonomyError.value = '请输入标签名称。';
    return;
  }
  taxonomyError.value = '';
  try {
    const response = await api.updateTag(tag.id, editingTagName.value.trim());
    tags.value = tags.value.map((item) => (item.id === tag.id ? response.data : item));
    cancelEditTag();
    showSuccess('标签已更新。');
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

async function removeTag(tag: BlogTag) {
  if (!globalThis.confirm(`确定停用标签“${tag.name}”？`)) {
    return;
  }
  taxonomyError.value = '';
  try {
    await api.deleteTag(tag.id);
    tags.value = tags.value.filter((item) => item.id !== tag.id);
    editor.tagIds = editor.tagIds.filter((id) => id !== tag.id);
    showSuccess('标签已停用。');
  } catch (error) {
    taxonomyError.value = getErrorMessage(error);
  }
}

function selectAssetUploadFile(event: globalThis.Event) {
  const input = event.target as globalThis.HTMLInputElement;
  assetUploadFile.value = input.files?.[0] ?? null;
}

async function uploadAssetFromLibrary() {
  if (!assetUploadFile.value) {
    showError('请先选择图片资产。');
    return;
  }
  busy.value = true;
  clearNotice();
  try {
    const response = await api.uploadAsset(assetUploadFile.value, 'blog_asset');
    assets.value = [
      response.data,
      ...assets.value.filter((asset) => asset.id !== response.data.id)
    ];
    assetUploadFile.value = null;
    if (assetInput.value) {
      assetInput.value.value = '';
    }
    showSuccess('资产已上传。');
  } catch (error) {
    showError(error);
  } finally {
    busy.value = false;
  }
}

function assetUrl(asset: Asset) {
  if (asset.publicUrl.startsWith('http://') || asset.publicUrl.startsWith('https://')) {
    return asset.publicUrl;
  }
  return `${baseUrl}${asset.publicUrl}`;
}

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
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
      asset_too_large: '图片不能超过 5MB。',
      asset_storage_error: '图片保存失败，请稍后重试。',
      category_name_required: '请输入分类名称。',
      tag_name_required: '请输入标签名称。'
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
          <button
            :class="{ active: activeView === 'assets' }"
            type="button"
            @click="activeView = 'assets'"
          >
            资产管理
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
              <img
                v-if="selectedCoverAsset"
                :alt="selectedCoverAsset.fileName"
                :src="assetUrl(selectedCoverAsset)"
              />
              <div>
                <span>{{ selectedCoverAsset?.fileName ?? '当前封面已选择' }}</span>
                <small v-if="selectedCoverAsset" class="muted">
                  {{ formatFileSize(selectedCoverAsset.size) }}
                </small>
              </div>
              <button class="quiet-button" type="button" @click="clearCover">移除封面</button>
            </div>
            <div class="cover-upload">
              <label>
                上传新封面
                <input ref="coverInput" accept="image/*" type="file" @change="selectCoverFile" />
              </label>
              <button :disabled="busy || !coverFile" type="button" @click="uploadCover">
                {{ busy ? '上传中...' : '上传封面' }}
              </button>
              <span v-if="coverFile" class="muted">{{ coverFile.name }}</span>
            </div>
            <div class="asset-picker">
              <div class="field-heading">
                <div>
                  <h3>选择已有封面</h3>
                  <p>复用资产库中的图片，保存文章后生效。</p>
                </div>
                <button class="quiet-button" type="button" @click="activeView = 'assets'">
                  打开资产库
                </button>
              </div>
              <div v-if="assets.length" class="asset-strip">
                <button
                  v-for="asset in assets.slice(0, 8)"
                  :key="asset.id"
                  class="asset-thumb"
                  :class="{ selected: editor.coverAssetId === asset.id }"
                  type="button"
                  @click="selectExistingCover(asset)"
                >
                  <img :alt="asset.fileName" :src="assetUrl(asset)" />
                  <span>{{ asset.fileName }}</span>
                </button>
              </div>
              <p v-else class="muted">资产库暂无图片，可以先上传新封面。</p>
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
            <MdEditor v-model="editor.body" language="zh-CN" :preview="true" />
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

        <section
          v-else-if="activeView === 'taxonomy'"
          class="content-section taxonomy-section"
          aria-labelledby="taxonomy-title"
        >
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
              <ul class="taxonomy-list">
                <li v-for="category in categories" :key="category.id">
                  <template v-if="editingCategoryId === category.id">
                    <input
                      v-model="editingCategoryName"
                      aria-label="分类名称"
                      class="inline-input"
                      type="text"
                    />
                    <button type="button" @click="saveCategory(category)">保存</button>
                    <button class="quiet-button" type="button" @click="cancelEditCategory">
                      取消
                    </button>
                  </template>
                  <template v-else>
                    <span>{{ category.name }}</span>
                    <button type="button" @click="startEditCategory(category)">重命名</button>
                    <button
                      class="quiet-button danger"
                      type="button"
                      @click="removeCategory(category)"
                    >
                      停用
                    </button>
                  </template>
                </li>
              </ul>
            </form>
            <form class="taxonomy-panel" @submit.prevent="addTag">
              <h3>标签</h3>
              <label>
                新标签名称
                <input v-model="newTag" type="text" />
              </label>
              <button type="submit">添加标签</button>
              <ul class="taxonomy-list">
                <li v-for="tag in tags" :key="tag.id">
                  <template v-if="editingTagId === tag.id">
                    <input
                      v-model="editingTagName"
                      aria-label="标签名称"
                      class="inline-input"
                      type="text"
                    />
                    <button type="button" @click="saveTag(tag)">保存</button>
                    <button class="quiet-button" type="button" @click="cancelEditTag">取消</button>
                  </template>
                  <template v-else>
                    <span>{{ tag.name }}</span>
                    <button type="button" @click="startEditTag(tag)">重命名</button>
                    <button class="quiet-button danger" type="button" @click="removeTag(tag)">
                      停用
                    </button>
                  </template>
                </li>
              </ul>
            </form>
          </div>
        </section>

        <section v-else class="content-section assets-section" aria-labelledby="assets-title">
          <div class="toolbar">
            <div>
              <h3 id="assets-title">资产库</h3>
              <p>上传和复用文章封面、正文配图等图片资产。</p>
            </div>
          </div>
          <form class="asset-upload-panel" @submit.prevent="uploadAssetFromLibrary">
            <label>
              上传图片
              <input
                ref="assetInput"
                accept="image/*"
                type="file"
                @change="selectAssetUploadFile"
              />
            </label>
            <button :disabled="busy || !assetUploadFile" type="submit">
              {{ busy ? '上传中...' : '上传资产' }}
            </button>
            <span v-if="assetUploadFile" class="muted">{{ assetUploadFile.name }}</span>
          </form>
          <div v-if="assets.length" class="asset-grid">
            <article v-for="asset in assets" :key="asset.id" class="asset-card">
              <img :alt="asset.fileName" :src="assetUrl(asset)" />
              <div>
                <strong>{{ asset.fileName }}</strong>
                <span class="muted">{{ formatFileSize(asset.size) }} · {{ asset.mimeType }}</span>
              </div>
              <div class="asset-actions">
                <a :href="assetUrl(asset)" target="_blank" rel="noreferrer">查看</a>
                <button type="button" @click="useAssetAsCover(asset)">设为封面</button>
              </div>
            </article>
          </div>
          <p v-else class="empty-state">资产库暂无图片。</p>
        </section>
      </section>
    </div>
  </main>
</template>
