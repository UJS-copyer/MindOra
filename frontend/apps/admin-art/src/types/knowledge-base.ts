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

export type KnowledgeRetrievalScope = 'all' | 'public'

/** 命中来源：仅向量召回、仅关键词召回、或两路都命中（两路都命中通常更可信）。 */
export type KnowledgeRetrievalMatchedBy = 'vector' | 'keyword' | 'both'

export interface KnowledgeRetrievalSearchParams {
  query: string
  scope: KnowledgeRetrievalScope
  topN?: number
  rerankTopK?: number
  rerankEnabled?: boolean
  /** 关闭后仅走向量召回，便于对比两路各自贡献了什么。 */
  keywordEnabled?: boolean
}

export interface KnowledgeRetrievalResultItem {
  chunkId: string
  documentId: string
  documentTitle: string
  sourceType: string
  sourcePath: string
  headingPath: string | null
  blockType: string
  codeLanguage: string | null
  /** 仅关键词命中时为 null。 */
  vectorScore: number | null
  rerankScore: number | null
  /** RRF 融合分（仅排名参与计算）。 */
  rrfScore: number | null
  /** 该切片在向量召回中的 1-based 排名。 */
  vectorRank: number | null
  /** 该切片在关键词召回中的 1-based 排名。 */
  keywordRank: number | null
  matchedBy: KnowledgeRetrievalMatchedBy
  contentPreview: string
  articleId: string | null
  articleSlug: string | null
}

export interface KnowledgeRetrievalSearchResponse {
  scope: string
  topN: number
  keywordTopN: number
  fusionTopM: number
  rerankTopK: number
  rerankEnabled: boolean
  keywordEnabled: boolean
  vectorCandidates: number
  keywordCandidates: number
  fallback: boolean
  fallbackReason: string | null
  keywordFallback: boolean
  keywordFallbackReason: string | null
  latencyMs: number
  results: KnowledgeRetrievalResultItem[]
}

