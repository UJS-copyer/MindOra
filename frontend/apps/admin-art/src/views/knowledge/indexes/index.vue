<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>索引状态</h2>
        <p>跟踪切片、embedding 和 Qdrant 索引写入状态。</p>
      </div>
      <ElSpace wrap>
        <ElButton @click="() => store.refresh()">刷新</ElButton>
        <ElButton type="primary" :disabled="selectedIds.length === 0" @click="rebuildSelected">
          批量重建
        </ElButton>
      </ElSpace>
    </div>

    <ElCard class="knowledge-card">
      <ElTable
        v-loading="store.loading || rebuilding"
        :data="store.indexRecords"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn type="selection" width="48" />
        <ElTableColumn label="文档" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.documentTitle }}</strong>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="chunkCount" label="Chunk" width="90" />
        <ElTableColumn label="切片状态" width="110">
          <template #default="{ row }">
            <ElTag :type="chunkStatus(row.chunkStatus).type">{{ chunkStatus(row.chunkStatus).label }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="索引状态" width="120">
          <template #default="{ row }">
            <ElTag :type="indexStatus(row.indexStatus).type">{{ indexStatus(row.indexStatus).label }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="embeddingModel" label="Embedding 模型" width="170" />
        <ElTableColumn prop="retryCount" label="重试次数" width="100" />
        <ElTableColumn label="失败原因" min-width="260">
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="muted-text">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="更新时间" width="180">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="rebuild([row.id])">重建</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'
  import type { KnowledgeIndexRecord, KnowledgeIndexStatus } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeIndexes' })

  const store = useKnowledgeBaseStore()
  const selectedIds = ref<string[]>([])
  const rebuilding = ref(false)

  const handleSelectionChange = (rows: KnowledgeIndexRecord[]) => {
    selectedIds.value = rows.map((row) => row.id)
  }

  const chunkStatus = (status: KnowledgeIndexRecord['chunkStatus']) => {
    const configs: Record<KnowledgeIndexRecord['chunkStatus'], { label: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
      ready: { label: '已切片', type: 'success' },
      pending: { label: '待处理', type: 'warning' },
      failed: { label: '失败', type: 'danger' }
    }
    return configs[status]
  }

  const indexStatus = (status: KnowledgeIndexStatus) => {
    const configs: Record<KnowledgeIndexStatus, { label: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
      ready: { label: '已写入', type: 'success' },
      processing: { label: '处理中', type: 'warning' },
      failed: { label: '失败', type: 'danger' },
      pending: { label: '待处理', type: 'info' }
    }
    return configs[status]
  }

  const rebuild = async (ids: string[]) => {
    rebuilding.value = true
    try {
      await store.rebuildIndexes(ids)
      ElMessage.success('索引重建任务已创建')
    } finally {
      rebuilding.value = false
    }
  }

  const rebuildSelected = async () => {
    await ElMessageBox.confirm(`确认重建 ${selectedIds.value.length} 个文档索引吗？`, '批量重建', {
      type: 'warning'
    })
    await rebuild(selectedIds.value)
  }

  const formatDate = (value: string) =>
    new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
      new Date(value)
    )

  onMounted(() => store.ensureLoaded())
</script>

<style scoped>
  .knowledge-page {
    padding: 20px;
  }

  .page-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
  }

  .page-heading h2 {
    margin: 0 0 6px;
    font-size: 20px;
  }

  .page-heading p,
  .muted-text {
    margin: 0;
    color: var(--art-gray-600);
  }

  .knowledge-card {
    border-radius: 8px;
  }

  .error-text {
    color: var(--el-color-danger);
  }
</style>
