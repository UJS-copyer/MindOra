<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>检索调试</h2>
        <p>测试混合检索与重排序效果，调试向量召回分值与匿名访客公开范围过滤规则。</p>
      </div>
    </div>

    <!-- 检索配置卡片 -->
    <ElCard class="knowledge-card">
      <template #header>
        <div class="card-heading">
          <span>检索参数配置</span>
          <ElTag type="primary">两阶段混合检索</ElTag>
        </div>
      </template>

      <ElForm :model="form" label-position="top">
        <ElRow :gutter="16">
          <ElCol :xs="24" :lg="16">
            <ElFormItem label="检索查询 (Query)" required>
              <ElInput
                v-model="form.query"
                placeholder="输入待检索的问题或关键词，按 Enter 键触发检索"
                clearable
                @keyup.enter="handleSearch"
              >
                <template #append>
                  <ElButton type="primary" :loading="searching" @click="handleSearch">
                    检索
                  </ElButton>
                </template>
              </ElInput>
            </ElFormItem>
          </ElCol>

          <ElCol :xs="24" :lg="8">
            <ElFormItem label="检索范围 (Scope)" required>
              <ElRadioGroup v-model="form.scope">
                <ElRadioButton value="all">全部 (all)</ElRadioButton>
                <ElRadioButton value="public">公开 (public)</ElRadioButton>
              </ElRadioGroup>
              <div class="scope-description">
                <span v-if="form.scope === 'all'">
                  <strong>全部</strong>：管理端全量视角，检索知识库中所有已索引切片（包含私有文档）。
                </span>
                <span v-else>
                  <strong>公开</strong>：模拟匿名访客视角，仅检索公开 (public)、活跃 (active) 且已启用知识库的文档。
                </span>
              </div>
            </ElFormItem>
          </ElCol>
        </ElRow>

        <ElRow :gutter="16" class="mt-2">
          <ElCol :xs="24" :sm="8" :md="6">
            <ElFormItem label="向量召回数 (topN)">
              <ElInputNumber
                v-model="form.topN"
                :min="1"
                :max="200"
                :step="5"
                placeholder="默认 30"
                class="full-width"
                controls-position="right"
              />
              <span class="field-tip">留空则使用后端默认值 30</span>
            </ElFormItem>
          </ElCol>

          <ElCol :xs="24" :sm="8" :md="6">
            <ElFormItem label="重排截断数 (rerankTopK)">
              <ElInputNumber
                v-model="form.rerankTopK"
                :min="1"
                :max="50"
                :step="1"
                placeholder="默认 8"
                class="full-width"
                controls-position="right"
                :disabled="!form.rerankEnabled"
              />
              <span class="field-tip">留空则使用后端默认值 8</span>
            </ElFormItem>
          </ElCol>

          <ElCol :xs="24" :sm="8" :md="6">
            <ElFormItem label="Rerank 重排序">
              <div class="switch-row">
                <ElSwitch v-model="form.rerankEnabled" />
                <span class="ml-2 text-xs text-zinc-500">
                  {{ form.rerankEnabled ? '开启二阶段重排' : '关闭（仅向量粗排）' }}
                </span>
              </div>
              <span class="field-tip">关闭时仅按 Qdrant 向量分数排序</span>
            </ElFormItem>
          </ElCol>

          <ElCol :xs="24" :sm="8" :md="6">
            <ElFormItem label="快捷重置">
              <div class="switch-row">
                <ElButton @click="resetForm">重置参数</ElButton>
              </div>
            </ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>
    </ElCard>

    <!-- 检索结果与状态展示 -->
    <ElCard class="knowledge-card" v-loading="searching">
      <template #header>
        <div class="card-heading">
          <span>检索结果</span>
          <span v-if="searchResponse" class="text-xs text-zinc-500 font-normal">
            共找到 <strong>{{ searchResponse.results.length }}</strong> 条匹配切片
          </span>
        </div>
      </template>

      <!-- 初始未检索提示 -->
      <div v-if="!hasSearched && !searchResponse" class="initial-placeholder">
        <p>请输入检索查询语句并点击「检索」开始调试</p>
      </div>

      <!-- 检索完成区域 -->
      <div v-else-if="searchResponse">
        <!-- 降级醒目提示条 -->
        <ElAlert
          v-if="searchResponse.fallback"
          type="warning"
          show-icon
          :closable="false"
          class="fallback-alert"
        >
          <template #title>
            <span class="font-semibold text-sm">检索服务已触发降级 (Fallback)</span>
          </template>
          <div class="mt-1 text-xs leading-relaxed">
            降级原因：{{ searchResponse.fallbackReason || '未提供具体降级原因，已自动回退至原始向量得分' }}
          </div>
        </ElAlert>

        <!-- 检索元数据指标栏 -->
        <div class="meta-bar">
          <div class="meta-item">
            <span class="meta-label">耗时：</span>
            <strong class="font-mono text-zinc-900 dark:text-zinc-100">{{ searchResponse.latencyMs }} ms</strong>
          </div>
          <div class="meta-item">
            <span class="meta-label">命中条数：</span>
            <strong class="font-mono text-zinc-900 dark:text-zinc-100">{{ searchResponse.results.length }}</strong>
          </div>
          <div class="meta-item">
            <span class="meta-label">生效范围：</span>
            <ElTag size="small" :type="searchResponse.scope === 'public' ? 'success' : 'info'">
              {{ searchResponse.scope === 'public' ? '公开 (public)' : '全部 (all)' }}
            </ElTag>
          </div>
          <div class="meta-item">
            <span class="meta-label">topN / rerankTopK：</span>
            <span class="font-mono text-zinc-700 dark:text-zinc-300">
              {{ searchResponse.topN }} / {{ searchResponse.rerankTopK }}
            </span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Rerank：</span>
            <ElTag size="small" :type="searchResponse.rerankEnabled ? 'primary' : 'info'">
              {{ searchResponse.rerankEnabled ? '开启' : '关闭' }}
            </ElTag>
          </div>
          <div class="meta-item">
            <span class="meta-label">降级状态：</span>
            <ElTag size="small" :type="searchResponse.fallback ? 'warning' : 'success'">
              {{ searchResponse.fallback ? '已降级' : '正常' }}
            </ElTag>
          </div>
        </div>

        <!-- 空结果展示 -->
        <div v-if="searchResponse.results.length === 0" class="empty-container">
          <ElEmpty description="未检索到匹配的切片内容">
            <template #description>
              <div class="empty-tip-box">
                <p>未找到与当前查询匹配的切片结果，可尝试更换关键词或调大 topN 召回数。</p>
                <p v-if="searchResponse.scope === 'public'" class="public-scope-warning">
                  💡 提示：当前检索范围为 <strong>公开 (public)</strong>，仅包含可见性为 public、状态为 active 且已启用知识库的文档。
                </p>
              </div>
            </template>
          </ElEmpty>
        </div>

        <!-- 结果表格 -->
        <ElTable
          v-else
          :data="searchResponse.results"
          row-key="chunkId"
          stripe
          class="results-table"
        >
          <ElTableColumn type="index" label="#" width="54" align="center" />

          <ElTableColumn label="文档与路径" min-width="220">
            <template #default="{ row }">
              <div class="doc-info">
                <span class="doc-title">{{ row.documentTitle }}</span>
                <div v-if="row.headingPath" class="doc-heading">
                  {{ row.headingPath }}
                </div>
                <div class="doc-path">
                  <span>{{ row.sourceType }}</span>
                  <span class="path-separator">/</span>
                  <span>{{ row.sourcePath }}</span>
                </div>
                <div v-if="row.articleSlug" class="doc-article-slug">
                  关联文章：{{ row.articleSlug }}
                </div>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn label="块类型" width="130">
            <template #default="{ row }">
              <div class="block-type-cell">
                <ElTag size="small" :type="row.blockType === 'code' ? 'warning' : 'info'">
                  {{ row.blockType }}
                </ElTag>
                <ElTag
                  v-if="row.codeLanguage"
                  size="small"
                  type="success"
                  class="code-lang-tag"
                >
                  {{ row.codeLanguage }}
                </ElTag>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn label="向量得分" width="110" align="right">
            <template #default="{ row }">
              <span class="score-text vector-score">
                {{ formatScore(row.vectorScore) }}
              </span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="Rerank 得分" width="120" align="right">
            <template #default="{ row }">
              <span
                v-if="row.rerankScore !== null && row.rerankScore !== undefined"
                class="score-text rerank-score"
              >
                {{ formatScore(row.rerankScore) }}
              </span>
              <span v-else class="muted-score">-</span>
            </template>
          </ElTableColumn>

          <ElTableColumn label="切片内容预览" min-width="320">
            <template #default="{ row }">
              <div class="content-preview-box">
                {{ row.contentPreview }}
              </div>
              <div class="chunk-meta-footer">
                <span>ID: {{ row.chunkId }}</span>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </ElCard>
  </div>
</template>

<script setup lang="ts">
  import { reactive, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { searchKnowledgeRetrieval } from '@/api/knowledge-base'
  import { isHttpError } from '@/utils/http/error'
  import type {
    KnowledgeRetrievalScope,
    KnowledgeRetrievalSearchParams,
    KnowledgeRetrievalSearchResponse
  } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeRetrieval' })

  interface SearchFormData {
    query: string
    scope: KnowledgeRetrievalScope
    topN: number | undefined
    rerankTopK: number | undefined
    rerankEnabled: boolean
  }

  interface ApiErrorBody {
    code?: string
    message?: string
    msg?: string
    traceId?: string
  }

  const searching = ref(false)
  const hasSearched = ref(false)
  const searchResponse = ref<KnowledgeRetrievalSearchResponse | null>(null)

  const form = reactive<SearchFormData>({
    query: '',
    scope: 'all',
    topN: undefined,
    rerankTopK: undefined,
    rerankEnabled: true
  })

  const resetForm = () => {
    form.query = ''
    form.scope = 'all'
    form.topN = undefined
    form.rerankTopK = undefined
    form.rerankEnabled = true
  }

  const formatScore = (score: number | null | undefined): string => {
    if (score === null || score === undefined) return '-'
    return score.toFixed(4)
  }

  const extractErrorMessage = (err: unknown): string => {
    if (isHttpError(err)) {
      const errorData = err.data as ApiErrorBody | undefined
      if (errorData?.message) return errorData.message
      if (errorData?.msg) return errorData.msg
      if (errorData?.code) return errorData.code
      return err.message || '检索请求失败'
    }
    if (err instanceof Error) {
      return err.message
    }
    return '检索请求发生未知错误'
  }

  const handleSearch = async () => {
    const trimmedQuery = form.query.trim()
    if (!trimmedQuery) {
      ElMessage.warning('请输入检索查询语句')
      return
    }

    // 严格保证 scope 必填
    const params: KnowledgeRetrievalSearchParams = {
      query: trimmedQuery,
      scope: form.scope,
      rerankEnabled: form.rerankEnabled
    }

    if (form.topN !== undefined && form.topN !== null) {
      params.topN = form.topN
    }
    if (form.rerankTopK !== undefined && form.rerankTopK !== null) {
      params.rerankTopK = form.rerankTopK
    }

    searching.value = true
    hasSearched.value = true

    try {
      searchResponse.value = await searchKnowledgeRetrieval(params)
    } catch (err: unknown) {
      const message = extractErrorMessage(err)
      ElMessage.error(message)
    } finally {
      searching.value = false
    }
  }
</script>

<style scoped>
  .knowledge-page {
    padding: 20px;
  }

  .page-heading,
  .card-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .page-heading {
    margin-bottom: 16px;
  }

  .page-heading h2 {
    margin: 0 0 6px;
    font-size: 20px;
    font-weight: 600;
  }

  .page-heading p {
    margin: 0;
    color: var(--art-gray-600);
  }

  .knowledge-card {
    margin-bottom: 16px;
    border-radius: 8px;
  }

  .full-width {
    width: 100%;
  }

  .field-tip {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .switch-row {
    display: flex;
    align-items: center;
    height: 32px;
  }

  .scope-description {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
  }

  .initial-placeholder {
    padding: 48px 0;
    text-align: center;
    color: var(--el-text-color-placeholder);
    font-size: 14px;
  }

  .fallback-alert {
    margin-bottom: 16px;
  }

  .meta-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 16px;
    padding: 10px 16px;
    margin-bottom: 16px;
    background-color: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    font-size: 12px;
  }

  .meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .meta-label {
    color: var(--el-text-color-secondary);
  }

  .empty-container {
    padding: 32px 0;
  }

  .empty-tip-box {
    max-width: 520px;
    margin: 8px auto 0;
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
  }

  .public-scope-warning {
    margin-top: 8px;
    padding: 8px 12px;
    background-color: var(--el-color-warning-light-9);
    border: 1px solid var(--el-color-warning-light-7);
    border-radius: 4px;
    color: var(--el-color-warning-dark-2);
    font-size: 12px;
  }

  .results-table {
    width: 100%;
  }

  .doc-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .doc-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.4;
  }

  .doc-heading {
    font-size: 12px;
    color: var(--el-text-color-regular);
  }

  .doc-path {
    font-size: 11px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    color: var(--el-text-color-secondary);
  }

  .path-separator {
    margin: 0 4px;
    color: var(--el-border-color);
  }

  .doc-article-slug {
    font-size: 11px;
    color: var(--el-color-primary);
  }

  .block-type-cell {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .code-lang-tag {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 11px;
  }

  .score-text {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
    font-weight: 600;
  }

  .vector-score {
    color: var(--el-color-primary);
  }

  .rerank-score {
    color: var(--el-color-success);
  }

  .muted-score {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  .content-preview-box {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 160px;
    overflow-y: auto;
    padding: 8px 12px;
    border-radius: 6px;
    background-color: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    user-select: text;
  }

  .chunk-meta-footer {
    margin-top: 4px;
    font-size: 11px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    color: var(--el-text-color-placeholder);
  }
</style>
