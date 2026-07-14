<script setup lang="ts">
import { createApiClient } from '@mindora/api-client';
import type { ArticleDraftInput, BlogArticle, BlogCategory, BlogTag } from '@mindora/types';
import { computed, onMounted, reactive, ref } from 'vue';
import { MdEditor } from 'md-editor-v3';
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
const activeView = ref<AdminView>('articles');
const articles = ref<BlogArticle[]>([]);
const categories = ref<BlogCategory[]>([]);
const tags = ref<BlogTag[]>([]);
const filterStatus = ref('all');
const editorId = ref<string | null>(null);
const newCategory = ref('');
const newTag = ref('');
const taxonomyError = ref('');

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

const editorTitle = computed(() => (editorId.value ? 'Edit article' : 'New article'));

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
  activeView.value = 'editor';
  notice.value = '';
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
    notice.value = getErrorMessage(error);
  } finally {
    busy.value = false;
  }
}

async function submitLogin() {
  loginError.value = '';
  if (!email.value.trim() || !password.value) {
    loginError.value = 'Enter your email and password.';
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
}

function startNewArticle() {
  resetEditor();
}

function editArticle(article: BlogArticle) {
  resetEditor(article);
}

async function saveArticle() {
  busy.value = true;
  notice.value = '';
  try {
    if (editorId.value) {
      await api.updateArticle(editorId.value, editor);
      notice.value = 'Draft updated.';
    } else {
      const response = await api.createArticle(editor);
      editorId.value = response.data.id;
      notice.value = 'Draft saved.';
    }
    await loadAdminData();
  } catch (error) {
    notice.value = getErrorMessage(error);
  } finally {
    busy.value = false;
  }
}

async function changePublication(article: BlogArticle) {
  busy.value = true;
  notice.value = '';
  try {
    if (article.status === 'published') {
      await api.unpublishArticle(article.id);
      notice.value = 'Article unpublished.';
    } else {
      await api.publishArticle(article.id);
      notice.value = 'Article published.';
    }
    await loadAdminData();
  } catch (error) {
    notice.value = getErrorMessage(error);
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
  return status.replace('_', ' ');
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date(value));
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed.';
}

onMounted(loadAdminData);
</script>

<template>
  <main class="admin-shell">
    <section v-if="!token" class="login-shell" aria-labelledby="admin-title">
      <p class="eyebrow">MindOra admin</p>
      <h1 id="admin-title">Sign in</h1>
      <p class="login-copy">Manage the public knowledge stream from one focused workspace.</p>
      <form @submit.prevent="submitLogin">
        <label>
          Email
          <input v-model="email" autocomplete="email" name="email" type="email" />
        </label>
        <label>
          Password
          <input
            v-model="password"
            autocomplete="current-password"
            name="password"
            type="password"
          />
        </label>
        <button :disabled="busy" type="submit">{{ busy ? 'Signing in...' : 'Sign in' }}</button>
      </form>
      <p v-if="loginError" class="status error" role="alert">{{ loginError }}</p>
    </section>

    <div v-else class="admin-layout">
      <aside class="admin-sidebar">
        <div>
          <p class="eyebrow">MindOra</p>
          <h1>Content desk</h1>
        </div>
        <nav aria-label="Admin navigation">
          <button
            :class="{ active: activeView === 'articles' }"
            type="button"
            @click="activeView = 'articles'"
          >
            Articles
          </button>
          <button
            :class="{ active: activeView === 'taxonomy' }"
            type="button"
            @click="activeView = 'taxonomy'"
          >
            Categories & tags
          </button>
        </nav>
        <button class="quiet-button" type="button" @click="logout">Sign out</button>
      </aside>

      <section class="admin-content">
        <header class="content-header">
          <div>
            <p class="eyebrow">Content management</p>
            <h2>{{ activeView === 'editor' ? editorTitle : activeView }}</h2>
          </div>
          <button v-if="activeView === 'articles'" type="button" @click="startNewArticle">
            New article
          </button>
          <button
            v-if="activeView === 'editor'"
            class="quiet-button"
            type="button"
            @click="activeView = 'articles'"
          >
            Back to list
          </button>
        </header>

        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <p v-if="busy" class="loading-line">Updating content...</p>

        <section
          v-if="activeView === 'articles'"
          class="content-section"
          aria-labelledby="articles-title"
        >
          <div class="toolbar">
            <div>
              <h3 id="articles-title">Articles</h3>
              <p>{{ articles.length }} managed articles</p>
            </div>
            <label class="compact-field">
              Status
              <select v-model="filterStatus">
                <option value="all">All</option>
                <option value="draft">Draft</option>
                <option value="published">Published</option>
                <option value="unpublished">Unpublished</option>
              </select>
            </label>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Status</th>
                  <th>Reads</th>
                  <th>Updated</th>
                  <th><span class="sr-only">Actions</span></th>
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
                    <button type="button" @click="editArticle(article)">Edit</button>
                    <button type="button" @click="changePublication(article)">
                      {{ article.status === 'published' ? 'Unpublish' : 'Publish' }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!filteredArticles.length">
                  <td class="empty-state" colspan="5">No articles match this filter.</td>
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
              Title
              <input v-model="editor.title" type="text" />
            </label>
            <label>
              Slug
              <input v-model="editor.slug" type="text" />
            </label>
            <label>
              Summary
              <textarea v-model="editor.summary" rows="3"></textarea>
            </label>
            <div class="field-grid">
              <label>
                Category
                <select v-model="editor.categoryId">
                  <option value="">Uncategorized</option>
                  <option v-for="category in categories" :key="category.id" :value="category.id">
                    {{ category.name }}
                  </option>
                </select>
              </label>
              <label>
                Visibility
                <select v-model="editor.visibility">
                  <option value="public">Public</option>
                  <option value="private">Private</option>
                </select>
              </label>
            </div>
            <label>
              Cover asset ID
              <input v-model="editor.coverAssetId" type="text" placeholder="Asset module ID" />
            </label>
            <fieldset>
              <legend>Tags</legend>
              <label v-for="tag in tags" :key="tag.id" class="check-label">
                <input v-model="editor.tagIds" :value="tag.id" type="checkbox" />
                {{ tag.name }}
              </label>
              <span v-if="!tags.length" class="muted">Create tags in Categories & tags.</span>
            </fieldset>
          </div>
          <div class="markdown-field">
            <div class="field-heading">
              <div>
                <h3 id="editor-title">Markdown body</h3>
                <p>Markdown is stored independently from public rendering.</p>
              </div>
              <button :disabled="busy" type="button" @click="saveArticle">
                {{ busy ? 'Saving...' : 'Save draft' }}
              </button>
            </div>
            <MdEditor v-model="editor.body" language="en-US" :preview="true" />
          </div>
        </section>

        <section v-else class="content-section taxonomy-section" aria-labelledby="taxonomy-title">
          <div class="toolbar">
            <div>
              <h3 id="taxonomy-title">Categories and tags</h3>
              <p>Keep taxonomy small and reusable across articles.</p>
            </div>
          </div>
          <p v-if="taxonomyError" class="status error" role="alert">{{ taxonomyError }}</p>
          <div class="taxonomy-grid">
            <form class="taxonomy-panel" @submit.prevent="addCategory">
              <h3>Categories</h3>
              <label>
                New category
                <input v-model="newCategory" type="text" />
              </label>
              <button type="submit">Add category</button>
              <ul>
                <li v-for="category in categories" :key="category.id">{{ category.name }}</li>
              </ul>
            </form>
            <form class="taxonomy-panel" @submit.prevent="addTag">
              <h3>Tags</h3>
              <label>
                New tag
                <input v-model="newTag" type="text" />
              </label>
              <button type="submit">Add tag</button>
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
