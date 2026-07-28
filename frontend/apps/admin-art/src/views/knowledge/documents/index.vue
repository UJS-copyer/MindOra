<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>知识文档</h2>
        <p>查看 Markdown 文档版本、可见性和是否进入知识库。</p>
      </div>
      <ElSpace>
        <ElSelect v-model="statusFilter" class="status-filter" aria-label="文档状态">
          <ElOption label="全部状态" value="all" />
          <ElOption label="已同步" value="synced" />
          <ElOption label="处理中" value="processing" />
          <ElOption label="失败" value="failed" />
        </ElSelect>
        <ElButton @click="store.refresh">刷新</ElButton>
      </ElSpace>
    </div>

    <ElCard class="knowledge-card">
      <ElTable v-loading="store.loading" :data="filteredDocuments" row-key="id">
        <ElTableColumn label="文档" min-width="280">
          <template #default="{ row }">
            <div class="document-title">
              <strong>{{ row.title }}</strong>
              <span>{{ row.sourcePath }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="version" label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="documentStatus(row.status).type">{{ documentStatus(row.status).label }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="可见性" width="100">
          <template #default="{ row }">
            <ElTag :type="row.visibility === 'public' ? 'success' : 'info'">
              {{ row.visibility === 'public' ? '公开' : '私有' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="图片资源" width="110">
          <template #default="{ row }">{{ row.imageReferences }} 个引用</template>
        </ElTableColumn>
        <ElTableColumn label="进入知识库" width="130">
          <template #default="{ row }">
            <ElSwitch
              :model-value="row.enabledForKnowledge"
              :loading="togglingId === row.id"
              @change="toggleKnowledge(row.id, Boolean($event))"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="更新时间" width="180">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" :loading="rebuildingId === row.id" @click="rebuildDocument(row.id)">
              重建索引
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'
  import type { KnowledgeDocumentStatus } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeDocuments' })

  const store = useKnowledgeBaseStore()
  const statusFilter = ref('all')
  const togglingId = ref('')
  const rebuildingId = ref('')

  const filteredDocuments = computed(() =>
    statusFilter.value === 'all'
      ? store.documents
      : store.documents.filter((item) => item.status === statusFilter.value)
  )

  const documentStatus = (status: KnowledgeDocumentStatus) => {
    const configs: Record<KnowledgeDocumentStatus, { label: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
      synced: { label: '已同步', type: 'success' },
      processing: { label: '处理中', type: 'warning' },
      failed: { label: '失败', type: 'danger' }
    }
    return configs[status]
  }

  const toggleKnowledge = async (documentId: string, enabled: boolean) => {
    togglingId.value = documentId
    try {
      await store.setDocumentEnabled(documentId, enabled)
      ElMessage.success(enabled ? '文档已纳入知识库' : '文档已移出知识库')
    } finally {
      togglingId.value = ''
    }
  }

  const rebuildDocument = async (documentId: string) => {
    rebuildingId.value = documentId
    try {
      const record = store.indexRecords.find((item) => item.documentId === documentId)
      if (!record) {
        ElMessage.warning('该文档尚未生成索引记录')
        return
      }
      await store.rebuildIndexes([record.id])
      ElMessage.success('单文档重建任务已创建')
    } finally {
      rebuildingId.value = ''
    }
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

  .page-heading p {
    margin: 0;
    color: var(--art-gray-600);
  }

  .knowledge-card {
    border-radius: 8px;
  }

  .status-filter {
    width: 130px;
  }

  .document-title {
    display: grid;
    gap: 5px;
  }

  .document-title span {
    overflow-wrap: anywhere;
    color: var(--art-gray-600);
    font-size: 12px;
  }
</style>
