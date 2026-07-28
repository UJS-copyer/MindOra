import request from '@/utils/http'
import type {
  KnowledgeBaseState,
  KnowledgeDataSource,
  KnowledgeDataSourceInput,
  KnowledgeIndexRecord,
  KnowledgeRagConfig,
  KnowledgeSyncMode,
  KnowledgeSyncTask
} from '@/types/knowledge-base'

// Temporary frontend facade. Set VITE_KNOWLEDGE_USE_MOCK=false when the backend contract is ready.
const useMock = import.meta.env.VITE_KNOWLEDGE_USE_MOCK !== 'false'
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
const now = () => new Date().toISOString()

const mockState: KnowledgeBaseState = {
  dataSources: [
    {
      id: 'source-gitee-main',
      name: 'MindOra 官方文档',
      provider: 'gitee',
      repositoryUrl: 'https://gitee.com/mindora/mindora-docs',
      branch: 'main',
      tokenMasked: '********demo',
      rootPath: 'docs',
      defaultVisibility: 'public',
      enabled: true,
      lastSyncAt: '2026-07-28T08:30:00.000Z',
      lastSyncMode: 'incremental'
    }
  ],
  syncTasks: [
    {
      id: 'sync-20260728-001',
      sourceId: 'source-gitee-main',
      sourceName: 'MindOra 官方文档',
      mode: 'incremental',
      status: 'success',
      progress: 100,
      documentsCreated: 3,
      documentsUpdated: 12,
      startedAt: '2026-07-28T08:26:00.000Z',
      finishedAt: '2026-07-28T08:30:00.000Z',
      errorMessage: null,
      retryCount: 0
    },
    {
      id: 'sync-20260727-004',
      sourceId: 'source-gitee-main',
      sourceName: 'MindOra 官方文档',
      mode: 'full',
      status: 'failed',
      progress: 64,
      documentsCreated: 0,
      documentsUpdated: 0,
      startedAt: '2026-07-27T06:10:00.000Z',
      finishedAt: '2026-07-27T06:18:00.000Z',
      errorMessage: 'Gitee API 请求超时，已保留本次同步游标',
      retryCount: 2
    }
  ],
  documents: [
    {
      id: 'doc-quick-start',
      sourceId: 'source-gitee-main',
      sourcePath: 'docs/getting-started/quick-start.md',
      title: '快速开始',
      version: 4,
      status: 'synced',
      visibility: 'public',
      enabledForKnowledge: true,
      imageReferences: 2,
      updatedAt: '2026-07-28T08:30:00.000Z'
    },
    {
      id: 'doc-api-auth',
      sourceId: 'source-gitee-main',
      sourcePath: 'docs/api/authentication.md',
      title: '认证与权限',
      version: 2,
      status: 'processing',
      visibility: 'private',
      enabledForKnowledge: true,
      imageReferences: 0,
      updatedAt: '2026-07-28T08:29:00.000Z'
    },
    {
      id: 'doc-release-notes',
      sourceId: 'source-gitee-main',
      sourcePath: 'docs/releases/2026-07.md',
      title: '2026 年 7 月更新说明',
      version: 1,
      status: 'failed',
      visibility: 'public',
      enabledForKnowledge: false,
      imageReferences: 4,
      updatedAt: '2026-07-28T08:18:00.000Z'
    }
  ],
  indexRecords: [
    {
      id: 'index-quick-start',
      documentId: 'doc-quick-start',
      documentTitle: '快速开始',
      chunkCount: 18,
      chunkStatus: 'ready',
      indexStatus: 'ready',
      embeddingModel: 'text-embedding-v4',
      errorMessage: null,
      retryCount: 0,
      updatedAt: '2026-07-28T08:31:00.000Z'
    },
    {
      id: 'index-api-auth',
      documentId: 'doc-api-auth',
      documentTitle: '认证与权限',
      chunkCount: 9,
      chunkStatus: 'ready',
      indexStatus: 'processing',
      embeddingModel: 'text-embedding-v4',
      errorMessage: null,
      retryCount: 0,
      updatedAt: '2026-07-28T08:29:00.000Z'
    },
    {
      id: 'index-release-notes',
      documentId: 'doc-release-notes',
      documentTitle: '2026 年 7 月更新说明',
      chunkCount: 0,
      chunkStatus: 'failed',
      indexStatus: 'failed',
      embeddingModel: 'text-embedding-v4',
      errorMessage: 'Embedding provider 返回 429，请稍后重试',
      retryCount: 1,
      updatedAt: '2026-07-28T08:20:00.000Z'
    }
  ],
  ragConfig: {
    chunkSize: 800,
    chunkOverlap: 120,
    strategy: 'markdown-heading',
    embeddingProvider: 'alibaba-bailian',
    embeddingModel: 'text-embedding-v4',
    embeddingDimensions: 1024,
    qdrantCollection: 'mindora-knowledge'
  }
}

let taskSequence = 10

function completeMockSync(taskId: string) {
  const task = mockState.syncTasks.find((item) => item.id === taskId)
  if (!task) return
  task.status = 'success'
  task.progress = 100
  task.documentsCreated = task.mode === 'full' ? 1 : 0
  task.documentsUpdated = task.mode === 'full' ? 15 : 3
  task.finishedAt = now()
  const source = mockState.dataSources.find((item) => item.id === task.sourceId)
  if (source) {
    source.lastSyncAt = task.finishedAt
    source.lastSyncMode = task.mode
  }
}

function completeMockRebuild(ids: string[]) {
  mockState.indexRecords.forEach((record) => {
    if (!ids.includes(record.id)) return
    record.chunkStatus = 'ready'
    record.indexStatus = 'ready'
    record.chunkCount = record.chunkCount || 12
    record.errorMessage = null
    record.updatedAt = now()
  })
}

export async function getKnowledgeBaseState(): Promise<KnowledgeBaseState> {
  if (!useMock) return request.get<KnowledgeBaseState>({ url: '/api/v1/admin/knowledge-base' })
  return clone(mockState)
}

export async function saveKnowledgeDataSource(
  input: KnowledgeDataSourceInput
): Promise<KnowledgeDataSource> {
  if (!useMock) {
    return request.put<KnowledgeDataSource>({
      url: `/api/v1/admin/knowledge-base/sources/${input.id || ''}`,
      data: input
    })
  }
  const existing = mockState.dataSources.find((item) => item.id === input.id)
  const source: KnowledgeDataSource = {
    id: existing?.id || `source-${Date.now()}`,
    name: input.name,
    provider: input.provider,
    repositoryUrl: input.repositoryUrl,
    branch: input.branch,
    tokenMasked:
      input.token && !input.token.startsWith('********')
        ? `********${input.token.slice(-4)}`
        : existing?.tokenMasked || '********demo',
    rootPath: input.rootPath,
    defaultVisibility: input.defaultVisibility,
    enabled: input.enabled,
    lastSyncAt: existing?.lastSyncAt || null,
    lastSyncMode: existing?.lastSyncMode || null
  }
  if (existing) Object.assign(existing, source)
  else mockState.dataSources.unshift(source)
  return clone(source)
}

export async function triggerKnowledgeSync(
  sourceId: string,
  mode: KnowledgeSyncMode
): Promise<KnowledgeSyncTask> {
  if (!useMock) {
    return request.post<KnowledgeSyncTask>({
      url: `/api/v1/admin/knowledge-base/sources/${sourceId}/sync`,
      data: { mode }
    })
  }
  const source = mockState.dataSources.find((item) => item.id === sourceId)
  if (!source) throw new Error('未找到知识库数据源')
  const task: KnowledgeSyncTask = {
    id: `sync-20260728-${String(taskSequence++).padStart(3, '0')}`,
    sourceId,
    sourceName: source.name,
    mode,
    status: 'running',
    progress: 18,
    documentsCreated: 0,
    documentsUpdated: 0,
    startedAt: now(),
    finishedAt: null,
    errorMessage: null,
    retryCount: 0
  }
  mockState.syncTasks.unshift(task)
  window.setTimeout(() => completeMockSync(task.id), 1200)
  return clone(task)
}

export async function retryKnowledgeSync(taskId: string): Promise<KnowledgeSyncTask> {
  if (!useMock) {
    return request.post<KnowledgeSyncTask>({
      url: `/api/v1/admin/knowledge-base/sync-tasks/${taskId}/retry`
    })
  }
  const task = mockState.syncTasks.find((item) => item.id === taskId)
  if (!task) throw new Error('未找到同步任务')
  task.status = 'running'
  task.progress = 24
  task.errorMessage = null
  task.finishedAt = null
  task.retryCount += 1
  window.setTimeout(() => completeMockSync(task.id), 1200)
  return clone(task)
}

export async function updateKnowledgeDocument(documentId: string, enabled: boolean): Promise<void> {
  if (!useMock) {
    await request.put<void>({
      url: `/api/v1/admin/knowledge-base/documents/${documentId}`,
      data: { enabledForKnowledge: enabled }
    })
    return
  }
  const document = mockState.documents.find((item) => item.id === documentId)
  if (document) document.enabledForKnowledge = enabled
}

export async function rebuildKnowledgeIndexes(indexIds: string[]): Promise<KnowledgeIndexRecord[]> {
  if (!useMock) {
    return request.post<KnowledgeIndexRecord[]>({
      url: '/api/v1/admin/knowledge-base/indexes/rebuild',
      data: { indexIds }
    })
  }
  mockState.indexRecords.forEach((record) => {
    if (indexIds.includes(record.id)) {
      record.indexStatus = 'processing'
      record.chunkStatus = 'pending'
      record.updatedAt = now()
    }
  })
  window.setTimeout(() => completeMockRebuild(indexIds), 1200)
  return clone(mockState.indexRecords.filter((record) => indexIds.includes(record.id)))
}

export async function saveKnowledgeRagConfig(config: KnowledgeRagConfig): Promise<KnowledgeRagConfig> {
  if (!useMock) {
    return request.put<KnowledgeRagConfig>({
      url: '/api/v1/admin/knowledge-base/rag-config',
      data: config
    })
  }
  Object.assign(mockState.ragConfig, config)
  return clone(mockState.ragConfig)
}
