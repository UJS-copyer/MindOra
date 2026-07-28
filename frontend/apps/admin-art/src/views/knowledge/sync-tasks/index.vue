<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>同步任务</h2>
        <p>查看增量/全量同步的异步进度、结果和失败原因。</p>
      </div>
      <ElButton @click="store.refresh">刷新任务</ElButton>
    </div>

    <div class="summary-grid">
      <ElCard v-for="item in summary" :key="item.label" class="summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </ElCard>
    </div>

    <ElCard class="knowledge-card">
      <ElTable v-loading="store.loading" :data="store.syncTasks" row-key="id">
        <ElTableColumn prop="sourceName" label="数据源" min-width="170" />
        <ElTableColumn label="模式" width="110">
          <template #default="{ row }">
            <ElTag :type="row.mode === 'full' ? 'warning' : 'info'">
              {{ row.mode === 'full' ? '全量' : '增量' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="120">
          <template #default="{ row }">
            <ElTag :type="statusConfig(row.status).type">{{ statusConfig(row.status).label }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="进度" min-width="180">
          <template #default="{ row }">
            <ElProgress :percentage="row.progress" :status="row.status === 'failed' ? 'exception' : undefined" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="文档变化" width="150">
          <template #default="{ row }">
            新增 {{ row.documentsCreated }} / 更新 {{ row.documentsUpdated }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="retryCount" label="重试次数" width="100" />
        <ElTableColumn label="开始时间" width="180">
          <template #default="{ row }">{{ formatDate(row.startedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="错误信息" min-width="240">
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else class="muted-text">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="row.status === 'failed'"
              link
              type="primary"
              :loading="retryingId === row.id"
              @click="retryTask(row.id)"
            >
              失败重试
            </ElButton>
            <span v-else class="muted-text">-</span>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'
  import type { KnowledgeTaskStatus } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeSyncTasks' })

  const store = useKnowledgeBaseStore()
  const retryingId = ref('')
  let refreshTimer: number | undefined

  const summary = computed(() => [
    { label: '全部任务', value: store.syncTasks.length },
    { label: '运行中', value: store.syncTasks.filter((item) => item.status === 'running').length },
    { label: '已成功', value: store.syncTasks.filter((item) => item.status === 'success').length },
    { label: '待处理失败', value: store.syncTasks.filter((item) => item.status === 'failed').length }
  ])

  const statusConfig = (status: KnowledgeTaskStatus) => {
    const configs: Record<KnowledgeTaskStatus, { label: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
      queued: { label: '排队中', type: 'info' },
      running: { label: '运行中', type: 'warning' },
      success: { label: '已完成', type: 'success' },
      failed: { label: '失败', type: 'danger' }
    }
    return configs[status]
  }

  const retryTask = async (taskId: string) => {
    retryingId.value = taskId
    try {
      await store.retrySync(taskId)
      ElMessage.success('重试任务已创建')
    } finally {
      retryingId.value = ''
    }
  }

  const formatDate = (value: string | null) =>
    value
      ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
          new Date(value)
        )
      : '-'

  onMounted(async () => {
    await store.ensureLoaded()
    refreshTimer = window.setInterval(() => store.refresh(), 1500)
  })

  onUnmounted(() => {
    if (refreshTimer) window.clearInterval(refreshTimer)
  })
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

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
    margin-bottom: 16px;
  }

  .summary-card {
    border-radius: 8px;
  }

  .summary-card span,
  .muted-text {
    color: var(--art-gray-600);
    font-size: 12px;
  }

  .summary-card strong {
    display: block;
    margin-top: 8px;
    font-size: 24px;
  }

  .knowledge-card {
    border-radius: 8px;
  }

  .error-text {
    color: var(--el-color-danger);
  }

  @media (max-width: 768px) {
    .summary-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
</style>
