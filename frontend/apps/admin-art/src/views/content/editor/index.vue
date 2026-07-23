<template>
  <div class="content-page">
    <ElCard class="content-card">
      <template #header>
        <div class="page-header">
          <div>
            <h2>{{ articleId ? '编辑文章' : '新建文章' }}</h2>
            <p>使用 Markdown 编写正文，并选择分类、标签和封面资产。</p>
          </div>
          <ElSpace>
            <ElButton @click="router.push({ name: 'ContentArticles' })">返回列表</ElButton>
            <ElButton type="primary" :loading="saving" @click="save">保存草稿</ElButton>
          </ElSpace>
        </div>
      </template>

      <ElForm label-position="top" class="editor-form">
        <ElRow :gutter="16">
          <ElCol :xs="24" :md="12">
            <ElFormItem label="标题">
              <ElInput v-model="form.title" placeholder="请输入文章标题" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :md="12">
            <ElFormItem label="Slug">
              <ElInput v-model="form.slug" placeholder="url-friendly-slug" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24">
            <ElFormItem label="摘要">
              <ElInput v-model="form.summary" type="textarea" :rows="3" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :md="8">
            <ElFormItem label="分类">
              <ElSelect v-model="form.categoryId" clearable placeholder="未分类">
                <ElOption
                  v-for="category in categories"
                  :key="category.id"
                  :label="category.name"
                  :value="category.id"
                />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :md="8">
            <ElFormItem label="可见范围">
              <ElSelect v-model="form.visibility">
                <ElOption label="公开" value="public" />
                <ElOption label="私有" value="private" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :md="8">
            <ElFormItem label="上传封面">
              <ElUpload
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                :on-change="uploadCover"
              >
                <ElButton :loading="uploading">选择图片</ElButton>
              </ElUpload>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24">
            <ElFormItem label="标签">
              <ElCheckboxGroup v-model="form.tagIds">
                <ElCheckbox v-for="tag in tags" :key="tag.id" :value="tag.id">
                  {{ tag.name }}
                </ElCheckbox>
              </ElCheckboxGroup>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" v-if="assets.length">
            <ElFormItem label="选择已有封面">
              <div class="asset-strip">
                <button
                  v-for="asset in assets.slice(0, 10)"
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
          </ElCol>
        </ElRow>

        <ElFormItem label="正文">
          <MdEditor v-model="form.body" language="zh-CN" :preview="true" />
        </ElFormItem>
      </ElForm>
    </ElCard>
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
    visibility: 'public'
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
    const article = (articleList as BlogArticle[]).find((item) => item.id === articleId.value)
    if (article) fillForm(article)
  }

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
      router.replace({ name: 'ContentEditor', query: { id: article.id } })
    } finally {
      saving.value = false
    }
  }

  onMounted(loadData)
</script>

<style scoped>
  .content-page {
    padding: 20px;
  }

  .content-card {
    border-radius: 8px;
  }

  .page-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .page-header h2 {
    margin: 0 0 6px;
    font-size: 20px;
  }

  .page-header p {
    margin: 0;
    color: var(--art-gray-600);
  }

  .editor-form {
    max-width: 1200px;
  }

  .asset-strip {
    display: grid;
    width: 100%;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 10px;
  }

  .asset-thumb {
    display: grid;
    gap: 6px;
    border: 1px solid var(--art-card-border);
    border-radius: 8px;
    padding: 8px;
    background: var(--default-box-color);
    color: var(--art-gray-700);
    text-align: left;
    cursor: pointer;
  }

  .asset-thumb.selected {
    border-color: var(--main-color);
    box-shadow: inset 0 0 0 1px var(--main-color);
  }

  .asset-thumb img {
    width: 100%;
    aspect-ratio: 4 / 3;
    border-radius: 6px;
    object-fit: cover;
    background: var(--art-gray-200);
  }

  .asset-thumb span {
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.md-editor) {
    min-height: 520px;
    border-radius: 8px;
  }
</style>
