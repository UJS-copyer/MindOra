<template>
  <div class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>知识库数据源</h2>
        <p>配置 Gitee Markdown 仓库，并从这里发起增量或全量同步。</p>
      </div>
      <ElTag type="info">Mock API</ElTag>
    </div>

    <ElRow :gutter="16">
      <ElCol :xs="24" :lg="16">
        <ElCard class="knowledge-card">
          <template #header>
            <div class="card-heading">
              <span>Gitee 配置</span>
              <ElTag type="success">Markdown</ElTag>
            </div>
          </template>
          <ElForm label-position="top" @submit.prevent="saveSource">
            <ElRow :gutter="16">
              <ElCol :xs="24" :md="12">
                <ElFormItem label="数据源名称">
                  <ElInput v-model="form.name" placeholder="例如：MindOra 官方文档" />
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :md="12">
                <ElFormItem label="仓库地址">
                  <ElInput v-model="form.repositoryUrl" placeholder="https://gitee.com/owner/repository" />
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :md="8">
                <ElFormItem label="分支">
                  <ElInput v-model="form.branch" placeholder="main" />
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :md="8">
                <ElFormItem label="根目录">
                  <ElInput v-model="form.rootPath" placeholder="docs" />
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :md="8">
                <ElFormItem label="默认可见性">
                  <ElSelect v-model="form.defaultVisibility" class="full-width">
                    <ElOption label="公开" value="public" />
                    <ElOption label="私有" value="private" />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24">
                <ElFormItem label="访问令牌">
                  <ElInput
                    v-model="form.token"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    placeholder="留空表示保持当前令牌"
                  />
                  <div class="field-help">保存后只展示掩码；访问令牌不会进入文档或任务列表。</div>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24">
                <ElFormItem label="启用数据源">
                  <ElSwitch v-model="form.enabled" />
                </ElFormItem>
              </ElCol>
            </ElRow>
            <div class="form-actions">
              <ElButton type="primary" :loading="store.loading" @click="saveSource">保存配置</ElButton>
              <ElButton @click="resetForm">恢复已保存配置</ElButton>
            </div>
          </ElForm>
        </ElCard>
      </ElCol>

      <ElCol :xs="24" :lg="8">
        <ElCard class="knowledge-card">
          <template #header>
            <div class="card-heading">
              <span>手动同步</span>
              <ElTag :type="form.enabled ? 'success' : 'info'">
                {{ form.enabled ? '已启用' : '已停用' }}
              </ElTag>
            </div>
          </template>
          <div class="sync-intro">
            <strong>{{ form.name || '未命名数据源' }}</strong>
            <span>{{ form.repositoryUrl || '尚未配置仓库地址' }}</span>
          </div>
          <ElRadioGroup v-model="syncMode" class="sync-mode">
            <ElRadio value="incremental" border>增量同步</ElRadio>
            <ElRadio value="full" border>全量同步</ElRadio>
          </ElRadioGroup>
          <ElAlert
            title="全量同步将重新扫描根目录下的 Markdown 文件，任务会通过异步流程执行。"
            type="info"
            :closable="false"
            show-icon
          />
          <ElButton
            class="sync-button"
            type="primary"
            :disabled="!form.enabled || !source"
            @click="triggerSync"
          >
            触发{{ syncMode === 'full' ? '全量' : '增量' }}同步
          </ElButton>
          <dl class="source-meta">
            <div>
              <dt>最近同步</dt>
              <dd>{{ formatDate(source?.lastSyncAt) }}</dd>
            </div>
            <div>
              <dt>最近模式</dt>
              <dd>{{ source?.lastSyncMode === 'full' ? '全量' : source?.lastSyncMode === 'incremental' ? '增量' : '-' }}</dd>
            </div>
          </dl>
        </ElCard>
      </ElCol>
    </ElRow>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useKnowledgeBaseStore } from '@/store/modules/knowledge-base'
  import type { KnowledgeDataSourceInput, KnowledgeSyncMode } from '@/types/knowledge-base'

  defineOptions({ name: 'KnowledgeSources' })

  const store = useKnowledgeBaseStore()
  const router = useRouter()
  const source = computed(() => store.dataSources[0])
  const syncMode = ref<KnowledgeSyncMode>('incremental')
  const form = reactive<KnowledgeDataSourceInput>({
    id: '',
    name: '',
    provider: 'gitee',
    repositoryUrl: '',
    branch: 'main',
    token: '',
    rootPath: 'docs',
    defaultVisibility: 'public',
    enabled: true
  })

  const fillForm = () => {
    if (!source.value) return
    Object.assign(form, {
      id: source.value.id,
      name: source.value.name,
      provider: source.value.provider,
      repositoryUrl: source.value.repositoryUrl,
      branch: source.value.branch,
      token: source.value.tokenMasked,
      rootPath: source.value.rootPath,
      defaultVisibility: source.value.defaultVisibility,
      enabled: source.value.enabled
    })
  }

  const resetForm = () => fillForm()

  const saveSource = async () => {
    if (!form.name.trim() || !form.repositoryUrl.trim()) {
      ElMessage.warning('请填写数据源名称和仓库地址')
      return
    }
    await store.saveSource({ ...form, name: form.name.trim(), repositoryUrl: form.repositoryUrl.trim() })
    fillForm()
    ElMessage.success('数据源配置已保存')
  }

  const triggerSync = async () => {
    await ElMessageBox.confirm(
      `确认对“${form.name}”执行${syncMode.value === 'full' ? '全量' : '增量'}同步吗？`,
      '触发同步',
      { type: syncMode.value === 'full' ? 'warning' : 'info' }
    )
    await store.triggerSync(source.value.id, syncMode.value)
    ElMessage.success('同步任务已创建')
    router.push({ name: 'KnowledgeSyncTasks' })
  }

  const formatDate = (value: string | null | undefined) =>
    value
      ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
          new Date(value)
        )
      : '尚未同步'

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
  .card-heading,
  .form-actions {
    display: flex;
    align-items: center;
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

  .page-heading p,
  .field-help {
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

  .field-help {
    margin-top: 6px;
    font-size: 12px;
  }

  .form-actions {
    justify-content: flex-start;
  }

  .sync-intro {
    display: grid;
    gap: 6px;
    margin-bottom: 18px;
  }

  .sync-intro span {
    overflow-wrap: anywhere;
    color: var(--art-gray-600);
    font-size: 12px;
  }

  .sync-mode {
    display: grid;
    gap: 10px;
    margin-bottom: 16px;
  }

  .sync-mode .el-radio {
    margin-right: 0;
  }

  .sync-button {
    width: 100%;
    margin-top: 18px;
  }

  .source-meta {
    display: grid;
    gap: 12px;
    margin: 20px 0 0;
  }

  .source-meta div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  .source-meta dt {
    color: var(--art-gray-600);
  }

  .source-meta dd {
    margin: 0;
    text-align: right;
  }
</style>
