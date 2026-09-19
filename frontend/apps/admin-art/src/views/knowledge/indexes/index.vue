<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>索引状态与切片检视</h2>
        <p>跟踪切片分块、embedding 和 Qdrant 向量写入状态，支持按需抽样检视切片文本。</p>
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
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <ElSpace :size="4">
              <ElButton link type="primary" @click="openChunkInspector(row as KnowledgeIndexRecord)">检视切片</ElButton>
              <ElButton link type="warning" @click="rebuild([row.id])">重建</ElButton>
            </ElSpace>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <!-- 切片检查器抽屉 (Chunk Inspector Drawer) -->
    <ElDrawer
      v-model="inspectorDrawerVisible"
      title="切片检查器 (Chunk Inspector)"
      size="560px"
      direction="rtl"
    >
      <div class="chunk-inspector-content">
        <div class="inspector-header mb-4 pb-3 border-b border-zinc-200 dark:border-zinc-800">
          <div class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 mb-1">
            {{ inspectorDocumentTitle }}
          </div>
          <div class="flex items-center gap-3 text-xs text-zinc-500">
            <span>切片数量: <strong>{{ inspectorChunks.length }}</strong></span>
            <span>·</span>
            <span>按需加载采样 (最大 50 条)</span>
          </div>
        </div>

        <div v-loading="inspectorLoading" class="chunk-list space-y-3">
          <div
            v-for="chunk in inspectorChunks"
            :key="chunk.id"
            class="chunk-item p-3.5 rounded-lg border border-zinc-200/70 dark:border-zinc-800 bg-zinc-50/70 dark:bg-zinc-900/50"
          >
            <div class="flex items-center justify-between text-xs mb-2">
              <span class="font-mono font-semibold text-emerald-600 dark:text-emerald-400">
                Chunk #{{ chunk.sequence }}
              </span>
              <div class="flex items-center gap-2">
                <span class="text-zinc-400 font-mono text-[11px]">{{ chunk.characterCount }} 字符</span>
                <ElTag size="small" :type="chunk.indexStatus === 'ready' ? 'success' : 'warning'">
                  {{ chunk.indexStatus }}
                </ElTag>
              </div>
            </div>

            <div class="text-xs text-zinc-700 dark:text-zinc-300 font-mono leading-relaxed bg-white/70 dark:bg-black/30 p-2.5 rounded border border-zinc-200/50 dark:border-zinc-800/80 whitespace-pre-wrap select-text max-h-48 overflow-y-auto">
              {{ chunk.content }}
            </div>

            <div v-if="chunk.vectorPointId" class="mt-2 text-[11px] text-zinc-400 flex items-center gap-1 font-mono">
              <span>Point ID:</span>
              <span class="text-zinc-600 dark:text-zinc-300">{{ chunk.vectorPointId }}</span>
            </div>
          </div>

          <div v-if="!inspectorLoading && !inspectorChunks.length" class="text-center py-12 text-zinc-400 text-xs">
            该文档暂无切片数据或尚未触发分块
          </div>
        </div>
      </div>
    </ElDrawer>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'
  import type { LiveChunk } from '@/api/knowledge-base'
  import type { KnowledgeIndexRecord, KnowledgeIndexStatus } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeIndexes' })

  const store = useKnowledgeBaseStore()
  const selectedIds = ref<string[]>([])
  const rebuilding = ref(false)

  // 切片检查器抽屉状态
  const inspectorDrawerVisible = ref(false)
  const inspectorDocumentTitle = ref('')
  const inspectorChunks = ref<LiveChunk[]>([])
  const inspectorLoading = ref(false)

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

  const openChunkInspector = async (row: KnowledgeIndexRecord) => {
    inspectorDocumentTitle.value = row.documentTitle
    inspectorDrawerVisible.value = true
    inspectorLoading.value = true
    inspectorChunks.value = []
    try {
      const chunks = await store.getDocumentChunks(row.id)
      inspectorChunks.value = chunks.slice(0, 50)
    } catch {
      ElMessage.error('获取切片详情失败')
    } finally {
      inspectorLoading.value = false
    }
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
