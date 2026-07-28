<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>RAG 配置</h2>
        <p>维护基础切片策略、embedding provider 和向量集合配置。</p>
      </div>
      <ElButton type="primary" :loading="saving" @click="saveConfig">保存配置</ElButton>
    </div>

    <ElRow :gutter="16">
      <ElCol :xs="24" :lg="12">
        <ElCard class="knowledge-card">
          <template #header>
            <div class="card-heading">
              <span>切片策略</span>
              <ElTag type="success">可编辑</ElTag>
            </div>
          </template>
          <ElForm label-position="top">
            <ElFormItem label="策略">
              <ElSegmented v-model="form.strategy" :options="strategyOptions" />
            </ElFormItem>
            <ElFormItem label="Chunk size">
              <ElInputNumber v-model="form.chunkSize" :min="200" :max="4000" :step="100" />
            </ElFormItem>
            <ElFormItem label="Overlap">
              <ElSlider v-model="form.chunkOverlap" :min="0" :max="600" :step="20" show-input />
            </ElFormItem>
          </ElForm>
        </ElCard>
      </ElCol>

      <ElCol :xs="24" :lg="12">
        <ElCard class="knowledge-card">
          <template #header>
            <div class="card-heading">
              <span>Embedding</span>
              <ElTag type="warning">预留适配器</ElTag>
            </div>
          </template>
          <ElForm label-position="top">
            <ElFormItem label="Provider">
              <ElSelect v-model="form.embeddingProvider" class="full-width">
                <ElOption label="Alibaba Cloud Bailian" value="alibaba-bailian" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="模型">
              <ElInput v-model="form.embeddingModel" />
            </ElFormItem>
            <ElFormItem label="维度">
              <ElInputNumber v-model="form.embeddingDimensions" :min="1" :max="4096" />
            </ElFormItem>
          </ElForm>
        </ElCard>
      </ElCol>

      <ElCol :xs="24">
        <ElCard class="knowledge-card">
          <template #header>
            <div class="card-heading">
              <span>Qdrant</span>
              <ElTag type="info">对话页未实现</ElTag>
            </div>
          </template>
          <ElForm label-position="top">
            <ElFormItem label="Collection">
              <ElInput v-model="form.qdrantCollection" placeholder="mindora-knowledge" />
            </ElFormItem>
          </ElForm>
          <ElDescriptions :column="3" border>
            <ElDescriptionsItem label="Markdown frontmatter">同步解析时不作为正文入库</ElDescriptionsItem>
            <ElDescriptionsItem label="图片资源">由文档版本关联资源记录</ElDescriptionsItem>
            <ElDescriptionsItem label="RAG 对话">Stage 3 接入</ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>
      </ElCol>
    </ElRow>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'
  import type { KnowledgeRagConfig } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeRagConfig' })

  const store = useKnowledgeBaseStore()
  const saving = ref(false)
  const strategyOptions = [
    { label: 'Markdown 标题', value: 'markdown-heading' },
    { label: '固定长度', value: 'fixed-size' },
    { label: '段落', value: 'paragraph' }
  ]

  const form = reactive<KnowledgeRagConfig>({
    chunkSize: 800,
    chunkOverlap: 120,
    strategy: 'markdown-heading',
    embeddingProvider: 'alibaba-bailian',
    embeddingModel: 'text-embedding-v4',
    embeddingDimensions: 1024,
    qdrantCollection: 'mindora-knowledge'
  })

  const fillForm = () => {
    if (store.ragConfig) Object.assign(form, store.ragConfig)
  }

  const saveConfig = async () => {
    saving.value = true
    try {
      await store.saveRag({ ...form })
      fillForm()
      ElMessage.success('RAG 配置已保存')
    } finally {
      saving.value = false
    }
  }

  onMounted(async () => {
    await store.ensureLoaded()
    fillForm()
  })
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
</style>
