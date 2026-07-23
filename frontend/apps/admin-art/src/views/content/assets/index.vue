<template>
  <div class="content-page">
    <ElCard class="content-card">
      <template #header>
        <div class="page-header">
          <div>
            <h2>资产库</h2>
            <p>上传和复用文章封面、正文配图等图片资产。</p>
          </div>
          <ElUpload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :on-change="handleFileChange"
          >
            <ElButton type="primary" :loading="uploading">上传图片</ElButton>
          </ElUpload>
        </div>
      </template>

      <ElRow v-loading="loading" :gutter="16">
        <ElCol v-for="asset in assets" :key="asset.id" :xs="24" :sm="12" :md="8" :lg="6">
          <ElCard class="asset-card" shadow="never">
            <img :src="assetUrl(asset)" :alt="asset.fileName" />
            <strong>{{ asset.fileName }}</strong>
            <span>{{ formatFileSize(asset.size) }} · {{ asset.mimeType }}</span>
            <ElButton link type="primary" @click="copyUrl(asset)">复制链接</ElButton>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElEmpty v-if="!loading && !assets.length" description="暂无资产" />
    </ElCard>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage, type UploadFile } from 'element-plus'
  import type { Asset } from '@mindora/types'
  import { assetUrl, listAssets, uploadAsset } from '@/api/content'

  defineOptions({ name: 'ContentAssets' })

  const loading = ref(false)
  const uploading = ref(false)
  const assets = ref<Asset[]>([])

  const loadAssets = async () => {
    loading.value = true
    try {
      assets.value = await listAssets()
    } finally {
      loading.value = false
    }
  }

  const handleFileChange = async (uploadFile: UploadFile) => {
    if (!uploadFile.raw) return
    uploading.value = true
    try {
      const asset = await uploadAsset(uploadFile.raw, 'blog_asset')
      assets.value = [asset, ...assets.value.filter((item) => item.id !== asset.id)]
      ElMessage.success('资产已上传')
    } finally {
      uploading.value = false
    }
  }

  const copyUrl = async (asset: Asset) => {
    await navigator.clipboard.writeText(assetUrl(asset))
    ElMessage.success('链接已复制')
  }

  const formatFileSize = (size: number) => {
    if (size < 1024) return `${size} B`
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
    return `${(size / 1024 / 1024).toFixed(1)} MB`
  }

  onMounted(loadAssets)
</script>

<style scoped>
  .content-page {
    padding: 20px;
  }

  .content-card {
    border-radius: 8px;
  }

  .page-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .page-header h2 {
    margin: 0 0 6px;
    font-size: 20px;
  }

  .page-header p {
    margin: 0;
    color: var(--art-gray-600);
  }

  .asset-card {
    display: grid;
    gap: 8px;
    margin-bottom: 16px;
    border-radius: 8px;
  }

  .asset-card img {
    width: 100%;
    aspect-ratio: 16 / 10;
    border-radius: 6px;
    object-fit: cover;
    background: var(--art-gray-200);
  }

  .asset-card span {
    color: var(--art-gray-600);
    font-size: 12px;
  }
</style>
