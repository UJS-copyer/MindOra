<!-- 知识中枢控制台 -->
<template>
  <div class="console-knowledge-studio space-y-6">
    <!-- 顶部核心 4 大指标卡 -->
    <ElRow :gutter="16">
      <!-- 指标 1：知识文档库 -->
      <ElCol :xs="24" :sm="12" :lg="6">
        <div class="metric-card glass-card p-5 rounded-xl relative overflow-hidden">
          <div class="flex items-center justify-between text-xs text-zinc-500 mb-2">
            <span class="font-medium tracking-wide">纳管知识文档</span>
            <span class="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
              </svg>
            </span>
          </div>
          <div class="text-3xl font-bold text-zinc-900 dark:text-zinc-50 font-mono tracking-tight mb-2">
            {{ totalDocs }}
            <span class="text-xs text-emerald-600 font-sans font-normal ml-1">+{{ activeDocs }} 活跃</span>
          </div>
          <div class="flex items-center justify-between text-[11px] text-zinc-400 pt-2 border-t border-zinc-100 dark:border-zinc-800">
            <span>已入索引: {{ indexedDocs }} 篇</span>
            <span>解析就绪率: {{ indexReadinessRate }}%</span>
          </div>
        </div>
      </ElCol>

      <!-- 指标 2：切片 Chunk 资产 -->
      <ElCol :xs="24" :sm="12" :lg="6">
        <div class="metric-card glass-card p-5 rounded-xl relative overflow-hidden">
          <div class="flex items-center justify-between text-xs text-zinc-500 mb-2">
            <span class="font-medium tracking-wide">向量化切片 Chunks</span>
            <span class="p-1.5 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <rect x="3" y="3" width="7" height="7" />
                <rect x="14" y="3" width="7" height="7" />
                <rect x="14" y="14" width="7" height="7" />
                <rect x="3" y="14" width="7" height="7" />
              </svg>
            </span>
          </div>
          <div class="text-3xl font-bold text-zinc-900 dark:text-zinc-50 font-mono tracking-tight mb-2">
            {{ totalChunks.toLocaleString() }}
            <span class="text-xs text-blue-600 font-sans font-normal ml-1">切片资产</span>
          </div>
          <div class="flex items-center justify-between text-[11px] text-zinc-400 pt-2 border-t border-zinc-100 dark:border-zinc-800">
            <span>平均切片长度: ~512 Tokens</span>
            <span>OverLap: 64 Tokens</span>
          </div>
        </div>
      </ElCol>

      <!-- 指标 3：Qdrant 向量维度 -->
      <ElCol :xs="24" :sm="12" :lg="6">
        <div class="metric-card glass-card p-5 rounded-xl relative overflow-hidden">
          <div class="flex items-center justify-between text-xs text-zinc-500 mb-2">
            <span class="font-medium tracking-wide">Qdrant 向量中枢</span>
            <span class="p-1.5 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
              </svg>
            </span>
          </div>
          <div class="text-3xl font-bold text-zinc-900 dark:text-zinc-50 font-mono tracking-tight mb-2">
            1536<span class="text-sm font-sans font-normal text-purple-500 ml-1">维</span>
            <span class="text-xs text-zinc-400 font-sans font-normal ml-2">Cosine 空间</span>
          </div>
          <div class="flex items-center justify-between text-[11px] text-zinc-400 pt-2 border-t border-zinc-100 dark:border-zinc-800">
            <span>集合状态: <strong class="text-emerald-500">Green</strong></span>
            <span>HNSW 索引: 开启</span>
          </div>
        </div>
      </ElCol>

      <!-- 指标 4：混合检索与 Rerank 状态 -->
      <ElCol :xs="24" :sm="12" :lg="6">
        <div class="metric-card glass-card p-5 rounded-xl relative overflow-hidden">
          <div class="flex items-center justify-between text-xs text-zinc-500 mb-2">
            <span class="font-medium tracking-wide">混合检索 Rerank</span>
            <span class="p-1.5 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" />
                <polyline points="12 6 12 12 14 14" />
              </svg>
            </span>
          </div>
          <div class="text-3xl font-bold text-zinc-900 dark:text-zinc-50 font-mono tracking-tight mb-2">
            42<span class="text-sm font-sans font-normal text-amber-500 ml-1">ms</span>
            <span class="text-xs text-zinc-400 font-sans font-normal ml-2">P95 延迟</span>
          </div>
          <div class="flex items-center justify-between text-[11px] text-zinc-400 pt-2 border-t border-zinc-100 dark:border-zinc-800">
            <span>Rerank 模型: BGE-Large</span>
            <span>Top-5 命中率: 94.2%</span>
          </div>
        </div>
      </ElCol>
    </ElRow>

    <!-- 知识库 5 步流水线全景监控 -->
    <div class="pipeline-section glass-card p-6 rounded-xl">
      <div class="flex items-center justify-between pb-4 mb-4 border-b border-zinc-100 dark:border-zinc-800">
        <div>
          <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">知识处理流水线全景监控</h3>
          <p class="text-xs text-zinc-400 mt-0.5">跟踪数据从外部抓取至向量数据库写入的全流程 5 大核心状态。</p>
        </div>
        <ElButton size="small" :loading="store.loading" @click="() => store.refresh()">
          刷新流水线
        </ElButton>
      </div>

      <!-- 5 步流水线流程条 -->
      <div class="grid grid-cols-1 sm:grid-cols-5 gap-3">
        <!-- Step 1: CRAWL -->
        <div class="step-card p-3.5 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 border border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center justify-between text-xs mb-1.5">
            <span class="font-semibold text-zinc-700 dark:text-zinc-300">1. 数据抓取</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-600 font-mono">CRAWL</span>
          </div>
          <div class="text-xs text-zinc-400">数据源数: {{ store.dataSources.length }} 个</div>
          <div class="mt-2 text-[11px] text-emerald-500 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            就绪运行中
          </div>
        </div>

        <!-- Step 2: PARSE -->
        <div class="step-card p-3.5 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 border border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center justify-between text-xs mb-1.5">
            <span class="font-semibold text-zinc-700 dark:text-zinc-300">2. 正文解析</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-600 font-mono">PARSE</span>
          </div>
          <div class="text-xs text-zinc-400">文档吞吐: {{ totalDocs }} 篇</div>
          <div class="mt-2 text-[11px] text-blue-500 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
            Markdown / HTML
          </div>
        </div>

        <!-- Step 3: CHUNK -->
        <div class="step-card p-3.5 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 border border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center justify-between text-xs mb-1.5">
            <span class="font-semibold text-zinc-700 dark:text-zinc-300">3. 智能切片</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-purple-500/10 text-purple-600 font-mono">CHUNK</span>
          </div>
          <div class="text-xs text-zinc-400">生成切片: {{ totalChunks }} 个</div>
          <div class="mt-2 text-[11px] text-purple-500 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-purple-500"></span>
            语义分段器
          </div>
        </div>

        <!-- Step 4: EMBED -->
        <div class="step-card p-3.5 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 border border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center justify-between text-xs mb-1.5">
            <span class="font-semibold text-zinc-700 dark:text-zinc-300">4. 向量嵌入</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-600 font-mono">EMBED</span>
          </div>
          <div class="text-xs text-zinc-400">模型: {{ embeddingModelName }}</div>
          <div class="mt-2 text-[11px] text-indigo-500 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-500"></span>
            1536 维生成
          </div>
        </div>

        <!-- Step 5: INDEX -->
        <div class="step-card p-3.5 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 border border-zinc-200/50 dark:border-zinc-800">
          <div class="flex items-center justify-between text-xs mb-1.5">
            <span class="font-semibold text-zinc-700 dark:text-zinc-300">5. Qdrant 写入</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-600 font-mono">INDEX</span>
          </div>
          <div class="text-xs text-zinc-400">写入就绪: {{ indexedDocs }} 篇</div>
          <div class="mt-2 text-[11px] text-emerald-500 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            持久化就绪
          </div>
        </div>
      </div>
    </div>

    <!-- 下半区：近期同步任务与流转日志 -->
    <ElRow :gutter="16">
      <ElCol :xs="24" :lg="16">
        <div class="glass-card p-5 rounded-xl">
          <div class="flex items-center justify-between pb-3 mb-3 border-b border-zinc-100 dark:border-zinc-800">
            <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">近期同步任务流转</h3>
            <span class="text-xs text-zinc-400">共 {{ store.syncTasks.length }} 条记录</span>
          </div>

          <ElTable :data="store.syncTasks.slice(0, 5)" stripe style="width: 100%">
            <ElTableColumn prop="id" label="任务 ID" width="160">
              <template #default="{ row }">
                <span class="font-mono text-xs">{{ row.id.slice(0, 8) }}...</span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="mode" label="同步模式" width="120">
              <template #default="{ row }">
                <ElTag size="small" :type="row.mode === 'full' ? 'primary' : 'success'">
                  {{ row.mode === 'full' ? '全量同步' : '增量同步' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="status" label="状态" width="110">
              <template #default="{ row }">
                <ElTag size="small" :type="getStatusType(row.status)">
                  {{ row.status }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="documentCount" label="文档数" width="90" />
            <ElTableColumn label="更新时间">
              <template #default="{ row }">
                <span class="text-xs text-zinc-400">{{ formatDate(row.updatedAt) }}</span>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </ElCol>

      <ElCol :xs="24" :lg="8">
        <div class="glass-card p-5 rounded-xl flex flex-col justify-between h-full">
          <div>
            <div class="flex items-center justify-between pb-3 mb-3 border-b border-zinc-100 dark:border-zinc-800">
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">知识工坊快捷通道</h3>
            </div>
            <div class="space-y-2.5">
              <div
                class="p-3 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer flex items-center justify-between"
                @click="router.push({ name: 'KnowledgeIndexes' })"
              >
                <div>
                  <div class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">切片抽屉检查器</div>
                  <div class="text-[11px] text-zinc-400">按需检视文档 chunk 文本与向量状态</div>
                </div>
                <span class="text-xs text-emerald-600">前往 →</span>
              </div>

              <div
                class="p-3 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer flex items-center justify-between"
                @click="router.push({ name: 'ContentEditor' })"
              >
                <div>
                  <div class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">沉浸式 Markdown 写作</div>
                  <div class="text-[11px] text-zinc-400">双区专注创作，一键纳管至知识库</div>
                </div>
                <span class="text-xs text-emerald-600">前往 →</span>
              </div>

              <div
                class="p-3 rounded-lg bg-zinc-50/70 dark:bg-zinc-800/40 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer flex items-center justify-between"
                @click="router.push({ name: 'KnowledgeRagConfig' })"
              >
                <div>
                  <div class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">RAG 与检索参数调优</div>
                  <div class="text-[11px] text-zinc-400">配置 Embedding 模型、Top-K 及重排权重</div>
                </div>
                <span class="text-xs text-emerald-600">前往 →</span>
              </div>
            </div>
          </div>

          <div class="mt-4 pt-3 border-t border-zinc-100 dark:border-zinc-800 flex items-center justify-between text-xs text-zinc-400">
            <span>MindOra Engine v2.4</span>
            <span class="text-emerald-500 font-medium">● 系统全链路正常</span>
          </div>
        </div>
      </ElCol>
    </ElRow>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'

  defineOptions({ name: 'Console' })

  const router = useRouter()
  const store = useKnowledgeBaseStore()

  const totalDocs = computed(() => store.documents.length)
  const activeDocs = computed(
    () => store.documents.filter((d) => d.status === 'synced' || d.enabledForKnowledge).length
  )
  const indexedDocs = computed(
    () => store.indexRecords.filter((r) => r.indexStatus === 'ready').length
  )
  const totalChunks = computed(() =>
    store.indexRecords.reduce((acc, r) => acc + (r.chunkCount || 0), 0)
  )
  const indexReadinessRate = computed(() => {
    if (!totalDocs.value) return 100
    return Math.round((indexedDocs.value / totalDocs.value) * 100)
  })

  const embeddingModelName = computed(() => {
    return store.ragConfig?.embeddingModel || 'text-embedding-3-small'
  })

  function getStatusType(status: string) {
    if (status === 'success' || status === 'completed') return 'success'
    if (status === 'running' || status === 'pending') return 'warning'
    if (status === 'failed') return 'danger'
    return 'info'
  }

  function formatDate(val?: string) {
    if (!val) return '-'
    return new Intl.DateTimeFormat('zh-CN', {
      dateStyle: 'short',
      timeStyle: 'short'
    }).format(new Date(val))
  }

  onMounted(() => {
    void store.ensureLoaded()
  })
</script>

<style scoped>
  .metric-card {
    transition: all 0.22s cubic-bezier(0.16, 1, 0.3, 1);
  }
  .metric-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 28px -4px rgba(0, 0, 0, 0.08);
  }
</style>
