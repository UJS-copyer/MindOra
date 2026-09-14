import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getKnowledgeBaseState,
  publishKnowledgeDocument,
  rebuildKnowledgeIndexes,
  retryKnowledgeSync,
  saveKnowledgeDataSource,
  saveKnowledgeRagConfig,
  triggerKnowledgeSync,
  updateKnowledgeDocument
} from '@/api/knowledge-base'
import type {
  KnowledgeBaseState,
  KnowledgeDataSource,
  KnowledgeDataSourceInput,
  KnowledgeIndexRecord,
  KnowledgeRagConfig,
  KnowledgeSyncMode,
  KnowledgeSyncTask
} from '@/types/knowledge-base'

export const useKnowledgeBaseStore = defineStore('knowledgeBaseStore', () => {
  const dataSources = ref<KnowledgeDataSource[]>([])
  const syncTasks = ref<KnowledgeSyncTask[]>([])
  const documents = ref<KnowledgeBaseState['documents']>([])
  const indexRecords = ref<KnowledgeIndexRecord[]>([])
  const ragConfig = ref<KnowledgeRagConfig>()
  const loading = ref(false)
  const initialized = ref(false)

  async function refresh(options: { silent?: boolean } = {}) {
    if (!options.silent) loading.value = true
    try {
      const state = await getKnowledgeBaseState()
      dataSources.value = state.dataSources
      syncTasks.value = state.syncTasks
      documents.value = state.documents
      indexRecords.value = state.indexRecords
      ragConfig.value = state.ragConfig
      initialized.value = true
    } finally {
      if (!options.silent) loading.value = false
    }
  }

  async function ensureLoaded() {
    if (!initialized.value) await refresh()
  }

  async function saveSource(input: KnowledgeDataSourceInput) {
    await saveKnowledgeDataSource(input)
    await refresh()
  }

  async function triggerSync(sourceId: string, mode: KnowledgeSyncMode) {
    const task = await triggerKnowledgeSync(sourceId, mode)
    await refresh()
    return task
  }

  async function retrySync(taskId: string) {
    const task = await retryKnowledgeSync(taskId)
    await refresh()
    return task
  }

  async function setDocumentEnabled(documentId: string, enabled: boolean) {
    await updateKnowledgeDocument(documentId, enabled)
    await refresh()
  }

  async function publishDocument(documentId: string) {
    const document = await publishKnowledgeDocument(documentId)
    await refresh()
    return document
  }

  async function rebuildIndexes(indexIds: string[]) {
    await rebuildKnowledgeIndexes(indexIds)
    await refresh()
  }

  async function saveRag(config: KnowledgeRagConfig) {
    await saveKnowledgeRagConfig(config)
    await refresh()
  }

  return {
    dataSources,
    syncTasks,
    documents,
    indexRecords,
    ragConfig,
    loading,
    initialized,
    refresh,
    ensureLoaded,
    saveSource,
    triggerSync,
    retrySync,
    setDocumentEnabled,
    publishDocument,
    rebuildIndexes,
    saveRag
  }
})
