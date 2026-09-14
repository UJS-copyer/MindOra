export type KnowledgeSyncMode = 'incremental' | 'full'
export type KnowledgeTaskStatus = 'queued' | 'running' | 'success' | 'failed'
export type KnowledgeDocumentStatus = 'synced' | 'processing' | 'failed'
export type KnowledgeIndexStatus = 'ready' | 'processing' | 'failed' | 'pending'

export interface KnowledgeDataSource {
  id: string
  name: string
  provider: 'gitee'
  repositoryUrl: string
  branch: string
  tokenMasked: string
  rootPath: string
  defaultVisibility: 'public' | 'private'
  enabled: boolean
  lastSyncAt: string | null
  lastSyncMode: KnowledgeSyncMode | null
}

export interface KnowledgeDataSourceInput {
  id?: string
  name: string
  provider: 'gitee'
  repositoryUrl: string
  branch: string
  token: string
  rootPath: string
  defaultVisibility: 'public' | 'private'
  enabled: boolean
}

export interface KnowledgeSyncTask {
  id: string
  sourceId: string
  sourceName: string
  mode: KnowledgeSyncMode
  status: KnowledgeTaskStatus
  progress: number
  documentsCreated: number
  documentsUpdated: number
  startedAt: string
  finishedAt: string | null
  errorMessage: string | null
  retryCount: number
}

export interface KnowledgeDocument {
  id: string
  sourceId: string
  sourceType: 'gitee' | 'blog'
  sourcePath: string
  title: string
  version: number
  status: KnowledgeDocumentStatus
  visibility: 'public' | 'private'
  enabledForKnowledge: boolean
  publicArticleId: string | null
  imageReferences: number
  updatedAt: string
}

export interface KnowledgeIndexRecord {
  id: string
  documentId: string
  documentTitle: string
  chunkCount: number
  chunkStatus: 'ready' | 'pending' | 'failed'
  indexStatus: KnowledgeIndexStatus
  embeddingModel: string
  errorMessage: string | null
  retryCount: number
  updatedAt: string
}

export interface KnowledgeRagConfig {
  chunkSize: number
  chunkOverlap: number
  strategy: 'markdown-heading' | 'fixed-size' | 'paragraph'
  embeddingProvider: string
  embeddingModel: string
  embeddingDimensions: number
  rerankProvider: string
  rerankModel: string
  qdrantCollection: string
}

export interface KnowledgeBaseState {
  dataSources: KnowledgeDataSource[]
  syncTasks: KnowledgeSyncTask[]
  documents: KnowledgeDocument[]
  indexRecords: KnowledgeIndexRecord[]
  ragConfig: KnowledgeRagConfig
}
