<template>
  <div class="content-page">
    <ElCard class="content-card">
      <template #header>
        <div class="page-header">
          <div>
            <h2>文章列表</h2>
            <p>管理 Stage 1 范围内的公开文章草稿、发布和下线状态。</p>
          </div>
          <ElSpace wrap>
            <ElSelect v-model="statusFilter" class="status-filter" aria-label="状态筛选">
              <ElOption label="全部" value="all" />
              <ElOption label="草稿" value="draft" />
              <ElOption label="已发布" value="published" />
              <ElOption label="已下线" value="unpublished" />
            </ElSelect>
            <ElButton type="primary" v-auth="'add'" @click="openEditor()">新建文章</ElButton>
          </ElSpace>
        </div>
      </template>

      <ElTable v-loading="loading" :data="filteredArticles" row-key="id">
        <ElTableColumn label="标题" min-width="260">
          <template #default="{ row }">
            <div class="article-title">
              <strong>{{ row.title }}</strong>
              <span>{{ row.slug }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="readCount" label="阅读" width="90" />
        <ElTableColumn label="更新时间" width="180">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <ElSpace>
              <ElButton
                v-if="row.status !== 'published'"
                link
                type="primary"
                v-auth="'edit'"
                @click="openEditor(row.id)"
              >
                编辑
              </ElButton>
              <ElButton
                link
                :type="row.status === 'published' ? 'warning' : 'success'"
                @click="togglePublish(row)"
              >
                {{ row.status === 'published' ? '下线' : '发布' }}
              </ElButton>
            </ElSpace>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import type { BlogArticle } from '@mindora/types'
  import { listAdminArticles, publishArticle, unpublishArticle } from '@/api/content'

  defineOptions({ name: 'ContentArticles' })

  const router = useRouter()
  const loading = ref(false)
  const statusFilter = ref('all')
  const articles = ref<BlogArticle[]>([])

  const filteredArticles = computed(() => {
    if (statusFilter.value === 'all') return articles.value
    return articles.value.filter((article) => article.status === statusFilter.value)
  })

  const loadArticles = async () => {
    loading.value = true
    try {
      articles.value = await listAdminArticles()
    } finally {
      loading.value = false
    }
  }

  const openEditor = (id?: string) => {
    router.push({ name: 'ContentEditor', query: id ? { id } : undefined })
  }

  const togglePublish = async (article: BlogArticle | any) => {
    loading.value = true
    try {
      if (article.status === 'published') {
        await unpublishArticle(article.id)
        ElMessage.success('文章已下线')
      } else {
        await publishArticle(article.id)
        ElMessage.success('文章已发布')
      }
      await loadArticles()
    } finally {
      loading.value = false
    }
  }

  const statusLabel = (status: string) => {
    const labels: Record<string, string> = {
      draft: '草稿',
      published: '已发布',
      unpublished: '已下线'
    }
    return labels[status] || status
  }

  const statusTag = (status: string) => {
    if (status === 'published') return 'success'
    if (status === 'unpublished') return 'warning'
    return 'info'
  }

  const formatDate = (value: string) =>
    new Intl.DateTimeFormat('zh-CN', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value))

  onMounted(loadArticles)
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

  .status-filter {
    width: 130px;
  }

  .article-title {
    display: grid;
    gap: 4px;
  }

  .article-title span {
    color: var(--art-gray-600);
    font-size: 12px;
  }
</style>
