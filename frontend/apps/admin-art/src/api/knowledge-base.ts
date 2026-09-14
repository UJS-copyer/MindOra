import request from '@/utils/http'
import type {
  KnowledgeBaseState,
  KnowledgeDataSource,
  KnowledgeDataSourceInput,
  KnowledgeIndexRecord,
  KnowledgeRagConfig,
  KnowledgeTaskStatus,
  KnowledgeSyncMode,
  KnowledgeSyncTask
} from '@/types/knowledge-base'

// Temporary frontend facade. Set VITE_KNOWLEDGE_USE_MOCK=true only for isolated UI demos.
const useMock = import.meta.env.VITE_KNOWLEDGE_USE_MOCK === 'true'
export const knowledgeApiUsesMock = useMock
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
      sourceType: 'gitee',
      sourcePath: 'docs/getting-started/quick-start.md',
      title: '快速开始',
      version: 4,
      status: 'synced',
      visibility: 'public',
      enabledForKnowledge: true,
      publicArticleId: 'article-quick-start',
      imageReferences: 2,
      updatedAt: '2026-07-28T08:30:00.000Z'
    },
    {
      id: 'doc-api-auth',
      sourceId: 'source-gitee-main',
      sourceType: 'gitee',
      sourcePath: 'docs/api/authentication.md',
      title: '认证与权限',
      version: 2,
      status: 'processing',
      visibility: 'private',
      enabledForKnowledge: true,
      publicArticleId: null,
      imageReferences: 0,
      updatedAt: '2026-07-28T08:29:00.000Z'
    },
    {
      id: 'doc-release-notes',
      sourceId: 'source-gitee-main',
      sourceType: 'gitee',
      sourcePath: 'docs/releases/2026-07.md',
      title: '2026 年 7 月更新说明',
      version: 1,
      status: 'failed',
      visibility: 'public',
      enabledForKnowledge: false,
      publicArticleId: null,
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
    rerankProvider: 'siliconflow',
    rerankModel: 'BAAI/bge-reranker-v2-m3',
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
  if (!useMock) {
    const [sources, tasks, documents, chunking, runtime] = await Promise.all([
      request.get<LiveDataSource[]>({ url: '/api/v1/admin/knowledge/sources' }),
      request.get<LiveSyncTask[]>({ url: '/api/v1/admin/knowledge/tasks' }),
      request.get<LiveDocument[]>({ url: '/api/v1/admin/knowledge/documents' }),
      request.get<LiveChunkingConfig>({ url: '/api/v1/admin/knowledge/config/chunking' }),
      request.get<LiveRuntimeConfig>({ url: '/api/v1/admin/knowledge/config/runtime' })
    ])
    const sourceNameById = new Map(sources.map((source) => [source.id, source.name]))
    const chunkEntries = await Promise.all(
      documents.map(async (document) => {
        const chunks = document.currentVersionId
          ? await request.get<LiveChunk[]>({
              url: `/api/v1/admin/knowledge/documents/${document.id}/chunks`
            })
          : []
        return [document.id, chunks] as const
      })
    )
    const chunksByDocumentId = new Map(chunkEntries)
    const ragConfig: KnowledgeRagConfig = {
      chunkSize: chunking.chunkSize,
      chunkOverlap: chunking.overlap,
      strategy: chunking.strategy === 'fixed_size' ? 'fixed-size' : 'markdown-heading',
      embeddingProvider: runtime.embeddingProvider,
      embeddingModel: runtime.embeddingModel,
      embeddingDimensions: runtime.embeddingDimensions,
      rerankProvider: runtime.rerankProvider,
      rerankModel: runtime.rerankModel,
      qdrantCollection: runtime.qdrantCollection
    }
    return {
      dataSources: sources.map(mapLiveDataSource),
      syncTasks: tasks.map((task) => mapLiveSyncTask(task, sourceNameById.get(task.sourceId))),
      documents: documents.map(mapLiveDocument),
      indexRecords: documents.map((document) =>
        mapLiveIndexRecord(document, chunksByDocumentId.get(document.id) || [], ragConfig.embeddingModel)
      ),
      ragConfig
    }
  }
  return clone(mockState)
}

export async function saveKnowledgeDataSource(
  input: KnowledgeDataSourceInput
): Promise<KnowledgeDataSource> {
  if (!useMock) {
    const data = {
      name: input.name,
      repositoryUrl: input.repositoryUrl,
      branch: input.branch,
      accessToken: input.token.startsWith('********') ? undefined : input.token,
      rootPath: input.rootPath,
      defaultVisibility: input.defaultVisibility,
      enabled: input.enabled
    }
    if (input.id) {
      const source = await request.put<LiveDataSource>({
        url: `/api/v1/admin/knowledge/sources/${input.id}`,
        data
      })
      return {
        ...input,
        id: source.id,
        tokenMasked: source.tokenConfigured ? '********configured' : '',
        lastSyncAt: null,
        lastSyncMode: null
      }
    }
    const source = await request.post<LiveDataSource>({
      url: '/api/v1/admin/knowledge/sources',
      data
    })
    return {
      ...input,
      id: source.id,
      tokenMasked: source.tokenConfigured ? '********configured' : '',
      lastSyncAt: null,
      lastSyncMode: null
    }
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
    const task = await request.post<LiveSyncTask>({
      url: `/api/v1/admin/knowledge/sources/${sourceId}/sync`,
      data: { mode }
    })
    return mapLiveSyncTask(task)
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
    const task = await request.post<LiveSyncTask>({
      url: `/api/v1/admin/knowledge/tasks/${taskId}/retry`
    })
    return mapLiveSyncTask(task)
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
      url: `/api/v1/admin/knowledge/documents/${documentId}`,
      data: { enabled }
    })
    return
  }
  const document = mockState.documents.find((item) => item.id === documentId)
  if (document) document.enabledForKnowledge = enabled
}

export async function publishKnowledgeDocument(documentId: string) {
  if (!useMock) {
    const document = await createKnowledgeArticleDraft(documentId)
    return mapLiveDocument(document)
  }
  const document = mockState.documents.find((item) => item.id === documentId)
  if (document) {
    document.publicArticleId = document.publicArticleId || `article-${document.id}`
    document.updatedAt = now()
  }
  return clone(document)
}

async function createKnowledgeArticleDraft(documentId: string): Promise<LiveDocument> {
  try {
    return await request.post<LiveDocument>({
      url: `/api/v1/admin/knowledge/documents/${documentId}/article-draft`,
      showErrorMessage: false
    })
  } catch (error) {
    if (typeof error === 'object' && error && 'code' in error && error.code === 404) {
      return request.post<LiveDocument>({
        url: `/api/v1/admin/knowledge/documents/${documentId}/publish`
      })
    }
    throw error
  }
}

export async function rebuildKnowledgeIndexes(indexIds: string[]): Promise<KnowledgeIndexRecord[]> {
  if (!useMock) {
    await request.post<number>({
      url: '/api/v1/admin/knowledge/documents/reindex',
      data: { documentIds: indexIds }
    })
    return []
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
    await request.put<unknown>({
      url: '/api/v1/admin/knowledge/config/chunking',
      data: {
        strategy: config.strategy === 'fixed-size' ? 'fixed_size' : 'markdown_heading',
        chunkSize: config.chunkSize,
        overlap: config.chunkOverlap,
        minimumSize: 40,
        maximumSize: config.chunkSize * 2,
        preserveHeadingHierarchy: true,
        includeMetadata: true
      }
    })
    return config
  }
  Object.assign(mockState.ragConfig, config)
  return clone(mockState.ragConfig)
}

export async function previewKnowledgeRerank(query: string, documents: string[]) {
  if (!useMock) {
    return request.post<LiveRerankResult[]>({
      url: '/api/v1/admin/knowledge/rerank/preview',
      data: { query, documents, topN: documents.length }
    })
  }
  return documents
    .map((document, index) => ({ index, score: 1 - index / Math.max(1, documents.length), document }))
    .sort((left, right) => right.score - left.score)
}

function mapLiveDataSource(source: LiveDataSource): KnowledgeDataSource {
  return {
    id: source.id,
    name: source.name,
    provider: 'gitee',
    repositoryUrl: source.repositoryUrl,
    branch: source.branch,
    tokenMasked: source.tokenConfigured ? '********configured' : '',
    rootPath: source.rootPath,
    defaultVisibility: source.defaultVisibility === 'private' ? 'private' : 'public',
    enabled: source.enabled,
    lastSyncAt: null,
    lastSyncMode: null
  }
}

function mapLiveSyncTask(task: LiveSyncTask, sourceName?: string): KnowledgeSyncTask {
  return {
    id: String(task.id),
    sourceId: task.sourceId,
    sourceName: sourceName || task.sourceId,
    mode: task.mode === 'full' ? 'full' : 'incremental',
    status: toKnowledgeTaskStatus(task.status),
    progress: task.totalFiles ? Math.round((task.processedFiles / task.totalFiles) * 100) : 0,
    documentsCreated: task.createdDocuments,
    documentsUpdated: task.updatedDocuments,
    startedAt: task.startedAt || task.createdAt,
    finishedAt: task.finishedAt,
    errorMessage: task.errorMessage,
    retryCount: task.retryCount
  }
}

function mapLiveDocument(document: LiveDocument) {
  return {
    id: document.id,
    sourceId: document.sourceId,
    sourceType: document.sourceType === 'blog' ? 'blog' : 'gitee',
    sourcePath: document.sourcePath,
    title: document.title,
    version: document.currentVersionId ? 1 : 0,
    status:
      document.indexStatus === 'failed'
        ? 'failed'
        : document.indexStatus === 'indexed'
          ? 'synced'
          : 'processing',
    visibility: document.visibility === 'private' ? 'private' : 'public',
    enabledForKnowledge: document.knowledgeEnabled,
    publicArticleId: document.publicArticleId,
    imageReferences: 0,
    updatedAt: document.updatedAt
  } satisfies KnowledgeBaseState['documents'][number]
}

function mapLiveIndexRecord(
  document: LiveDocument,
  chunks: LiveChunk[],
  embeddingModel: string
): KnowledgeIndexRecord {
  const failedChunk = chunks.find((chunk) => chunk.indexStatus === 'failed')
  return {
    id: document.id,
    documentId: document.id,
    documentTitle: document.title,
    chunkCount: chunks.length,
    chunkStatus: failedChunk
      ? 'failed'
      : chunks.length === 0 || document.indexStatus === 'pending'
        ? 'pending'
        : 'ready',
    indexStatus:
      document.indexStatus === 'indexed'
        ? 'ready'
        : document.indexStatus === 'failed'
          ? 'failed'
          : document.indexStatus === 'pending'
            ? 'pending'
            : 'processing',
    embeddingModel: chunks.find((chunk) => chunk.embeddingModel)?.embeddingModel || embeddingModel,
    errorMessage: document.indexFailureReason || failedChunk?.failureReason || null,
    retryCount: document.indexRetryCount,
    updatedAt: document.updatedAt
  }
}

interface LiveDataSource {
  id: string
  name: string
  repositoryUrl: string
  branch: string
  tokenConfigured: boolean
  rootPath: string
  defaultVisibility: string
  enabled: boolean
}

interface LiveSyncTask {
  id: number
  sourceId: string
  mode: string
  status: string
  totalFiles: number
  processedFiles: number
  createdDocuments: number
  updatedDocuments: number
  retryCount: number
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

interface LiveDocument {
  id: string
  sourceType: string
  sourceId: string
  sourcePath: string
  title: string
  currentVersionId: string | null
  visibility: string
  knowledgeEnabled: boolean
  publicArticleId: string | null
  indexStatus: string
  indexFailureReason: string | null
  indexRetryCount: number
  updatedAt: string
}

interface LiveChunkingConfig {
  strategy: string
  chunkSize: number
  overlap: number
  minimumSize: number
  maximumSize: number
  preserveHeadingHierarchy: boolean
  includeMetadata: boolean
}

interface LiveRuntimeConfig {
  embeddingProvider: string
  embeddingModel: string
  embeddingDimensions: number
  rerankProvider: string
  rerankModel: string
  qdrantCollection: string
}

interface LiveRerankResult {
  index: number
  score: number
  document: string
}

interface LiveChunk {
  id: string
  documentVersionId: string
  sequence: number
  content: string
  characterCount: number
  indexStatus: string
  embeddingModel: string | null
  vectorPointId: string | null
  failureReason: string | null
  retryCount: number
}

function toKnowledgeTaskStatus(status: string): KnowledgeTaskStatus {
  if (status === 'running' || status === 'success' || status === 'failed') return status
  return 'queued'
}
