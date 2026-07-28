import { AppRouteRecord } from '@/types/router'

export const knowledgeRoutes: AppRouteRecord = {
  path: '/knowledge',
  name: 'Knowledge',
  component: '/index/index',
  meta: {
    title: 'menus.knowledge.title',
    icon: 'ri:book-open-line',
    roles: ['R_SUPER', 'R_ADMIN']
  },
  children: [
    {
      path: 'sources',
      name: 'KnowledgeSources',
      component: '/knowledge/sources',
      meta: { title: 'menus.knowledge.sources', keepAlive: true, roles: ['R_SUPER', 'R_ADMIN'] }
    },
    {
      path: 'sync-tasks',
      name: 'KnowledgeSyncTasks',
      component: '/knowledge/sync-tasks',
      meta: { title: 'menus.knowledge.syncTasks', keepAlive: true, roles: ['R_SUPER', 'R_ADMIN'] }
    },
    {
      path: 'documents',
      name: 'KnowledgeDocuments',
      component: '/knowledge/documents',
      meta: { title: 'menus.knowledge.documents', keepAlive: true, roles: ['R_SUPER', 'R_ADMIN'] }
    },
    {
      path: 'indexes',
      name: 'KnowledgeIndexes',
      component: '/knowledge/indexes',
      meta: { title: 'menus.knowledge.indexes', keepAlive: true, roles: ['R_SUPER', 'R_ADMIN'] }
    },
    {
      path: 'rag-config',
      name: 'KnowledgeRagConfig',
      component: '/knowledge/rag-config',
      meta: { title: 'menus.knowledge.ragConfig', keepAlive: true, roles: ['R_SUPER', 'R_ADMIN'] }
    }
  ]
}
