<template>
  <div class="content-editor-studio">
    <!-- 顶部动作条 -->
    <div class="glass-card mb-4 p-4 rounded-xl flex items-center justify-between">
      <div class="flex items-center gap-3">
        <ElButton @click="router.push({ name: 'ContentArticles' })">
          ← 返回列表
        </ElButton>
        <div>
          <h2 class="text-base font-semibold text-zinc-900 dark:text-zinc-100 m-0">
            {{ articleId ? '编辑文章' : '新建文章' }}
          </h2>
          <span class="text-xs text-zinc-400">双区专注 Markdown 创作与知识中枢联动</span>
        </div>
      </div>

      <ElSpace>
        <ElButton
          type="info"
          plain
          @click="isInspectorOpen = !isInspectorOpen"
        >
          {{ isInspectorOpen ? '收起属性抽屉' : '展开属性抽屉' }}
        </ElButton>
        <ElButton type="primary" :loading="saving" @click="save">
          保存并发布草稿
        </ElButton>
      </ElSpace>
    </div>

    <!-- 双区布局 -->
    <div class="flex flex-col lg:flex-row gap-4 items-start">
      <!-- 左侧：沉浸写作画布 (80% 宽度) -->
      <div
        class="glass-card p-5 rounded-xl transition-all duration-300 w-full"
        :class="isInspectorOpen ? 'lg:w-[75%]' : 'lg:w-full'"
      >
        <ElForm label-position="top">
          <!-- 标题与 Slug 输入行 -->
          <ElRow :gutter="16" class="mb-2">
            <ElCol :xs="24" :md="16">
              <ElFormItem label="文章标题" required>
                <ElInput
                  v-model="form.title"
                  placeholder="请输入引人入胜的文章标题..."
                  size="large"
                  class="font-semibold text-lg"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :xs="24" :md="8">
              <ElFormItem label="永久链接 Slug" required>
                <ElInput
                  v-model="form.slug"
                  placeholder="url-friendly-slug"
                  size="large"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>

          <!-- 全宽 Markdown 编辑器 -->
          <ElFormItem label="Markdown 正文" class="m-0">
            <MdEditor
              v-model="form.body"
              class="body-editor"
              language="zh-CN"
              :preview="true"
            />
          </ElFormItem>
        </ElForm>
      </div>

      <!-- 右侧：元数据与知识库属性抽屉 (25% 宽度) -->
      <div
        v-show="isInspectorOpen"
        class="glass-card p-5 rounded-xl w-full lg:w-[25%] flex-shrink-0 space-y-5"
      >
        <div class="flex items-center justify-between pb-3 border-b border-zinc-100 dark:border-zinc-800">
          <span class="text-xs font-semibold text-zinc-900 dark:text-zinc-100 uppercase tracking-wider">
            文章发布属性
          </span>
          <span class="text-[11px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-600 font-mono">
            Inspector
          </span>
        </div>

        <ElForm label-position="top" class="space-y-4">
          <!-- 分类 -->
          <ElFormItem label="知识库分类">
            <ElSelect v-model="form.categoryId" clearable placeholder="选择分类" class="w-full">
              <ElOption
                v-for="category in categories"
                :key="category.id"
                :label="category.name"
                :value="category.id"
              />
            </ElSelect>
          </ElFormItem>

          <!-- 可见范围 -->
          <ElFormItem label="可见范围">
            <ElSelect v-model="form.visibility" class="w-full">
              <ElOption label="公开 (全站可见)" value="public" />
              <ElOption label="私有 (仅管理员可见)" value="private" />
            </ElSelect>
          </ElFormItem>

          <!-- 进入知识库 -->
          <ElFormItem label="自动纳入知识中枢">
            <div class="flex items-center justify-between w-full p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-800/50 border border-zinc-200/60 dark:border-zinc-800">
              <span class="text-xs text-zinc-600 dark:text-zinc-400">Qdrant 向量切片</span>
              <ElSwitch v-model="form.knowledgeEnabled" active-text="开启" inactive-text="关闭" />
            </div>
          </ElFormItem>

          <!-- 标签多选 -->
          <ElFormItem label="标签关联">
            <ElCheckboxGroup v-model="form.tagIds" class="flex flex-wrap gap-1.5">
              <ElCheckbox
                v-for="tag in tags"
                :key="tag.id"
                :value="tag.id"
                border
                size="small"
              >
                {{ tag.name }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </ElFormItem>

          <!-- 核心摘要 -->
          <ElFormItem label="文章核心摘要">
            <ElInput
              v-model="form.summary"
              type="textarea"
              :rows="4"
              placeholder="简要概括文章核心思考，展示于前台卡片与搜索引用..."
            />
          </ElFormItem>

          <!-- 上传封面 -->
          <ElFormItem label="文章封面图片">
            <ElUpload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="uploadCover"
              class="w-full"
            >
              <ElButton :loading="uploading" class="w-full">
                本地上传封面
              </ElButton>
            </ElUpload>
          </ElFormItem>

          <!-- 已有资产库封面选择 -->
          <ElFormItem v-if="assets.length" label="或从资产库快速选择">
            <div class="asset-strip max-h-48 overflow-y-auto pr-1">
              <button
                v-for="asset in assets.slice(0, 8)"
                :key="asset.id"
                class="asset-thumb"
                :class="{ selected: form.coverAssetId === asset.id }"
                type="button"
                @click="form.coverAssetId = asset.id"
              >
                <img :src="assetUrl(asset)" :alt="asset.fileName" />
                <span>{{ asset.fileName }}</span>
              </button>
            </div>
          </ElFormItem>

          <!-- 底部保存 -->
          <div class="pt-3 border-t border-zinc-100 dark:border-zinc-800">
            <ElButton type="primary" :loading="saving" class="w-full" @click="save">
              保存草稿
            </ElButton>
          </div>
        </ElForm>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage, type UploadFile } from 'element-plus'
  import { MdEditor } from 'md-editor-v3'
  import 'md-editor-v3/lib/style.css'
  import type { ArticleDraftInput, Asset, BlogArticle, BlogCategory, BlogTag } from '@mindora/types'
  import {
    assetUrl,
    createArticle,
    listAdminArticles,
    listAssets,
    listCategories,
    listTags,
    updateArticle,
    uploadAsset
  } from '@/api/content'

  defineOptions({ name: 'ContentEditor' })

  const router = useRouter()
  const route = useRoute()
  const articleId = ref(typeof route.query.id === 'string' ? route.query.id : '')
  const saving = ref(false)
  const uploading = ref(false)
  const isInspectorOpen = ref(true)

  const categories = ref<BlogCategory[]>([])
  const tags = ref<BlogTag[]>([])
  const assets = ref<Asset[]>([])

  const form = reactive<ArticleDraftInput>({
    title: '',
    slug: '',
    summary: '',
    body: '',
    coverAssetId: '',
    categoryId: '',
    tagIds: [],
    visibility: 'public',
    knowledgeEnabled: true
  })

  const fillForm = (article: BlogArticle) => {
    form.title = article.title
    form.slug = article.slug
    form.summary = article.summary || ''
    form.body = article.body
    form.coverAssetId = article.coverAssetId || ''
    form.categoryId = article.categoryId || ''
    form.tagIds = [...(article.tagIds || [])]
    form.visibility = article.visibility || 'public'
    form.knowledgeEnabled = article.knowledgeEnabled ?? true
  }

  const loadData = async () => {
    const [categoryList, tagList, assetList, articleList] = await Promise.all([
      listCategories(),
      listTags(),
      listAssets(),
      articleId.value ? listAdminArticles() : Promise.resolve([])
    ])
    categories.value = categoryList
    tags.value = tagList
    assets.value = assetList
    const article = (articleList as BlogArticle[]).find(
      (item) => normalizeId(item.id) === normalizeId(articleId.value)
    )
    if (article) fillForm(article)
  }

  const normalizeId = (id: string) => id.replace(/-/g, '').toLowerCase()

  const uploadCover = async (uploadFile: UploadFile) => {
    if (!uploadFile.raw) return
    uploading.value = true
    try {
      const asset = await uploadAsset(uploadFile.raw, 'blog_cover')
      assets.value = [asset, ...assets.value.filter((item) => item.id !== asset.id)]
      form.coverAssetId = asset.id
      ElMessage.success('封面已上传')
    } finally {
      uploading.value = false
    }
  }

  const save = async () => {
    saving.value = true
    try {
      const payload: ArticleDraftInput = {
        ...form,
        summary: form.summary || undefined,
        coverAssetId: form.coverAssetId || undefined,
        categoryId: form.categoryId || undefined,
        tagIds: form.tagIds || []
      }
      const article = articleId.value
        ? await updateArticle(articleId.value, payload)
        : await createArticle(payload)
      articleId.value = article.id
      ElMessage.success('草稿已保存')
      router.push({ name: 'ContentArticles' })
    } finally {
      saving.value = false
    }
  }

  onMounted(loadData)
</script>

<style scoped>
  .content-editor-studio {
    padding: 16px;
  }

  :deep(.body-editor) {
    height: min(78vh, 920px);
    min-height: 680px;
    border-radius: 8px;
  }

  .asset-strip {
    display: grid;
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .asset-thumb {
    display: grid;
    gap: 4px;
    border: 1px solid var(--art-card-border);
    border-radius: 6px;
    padding: 6px;
    background: var(--default-box-color);
    color: var(--art-gray-700);
    text-align: left;
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .asset-thumb.selected {
    border-color: #059669;
    box-shadow: 0 0 0 1px #059669;
  }

  .asset-thumb img {
    width: 100%;
    aspect-ratio: 4 / 3;
    border-radius: 4px;
    object-fit: cover;
    background: var(--art-gray-200);
  }

  .asset-thumb span {
    overflow: hidden;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
