<template>
  <div class="content-page">
    <ElRow :gutter="16">
      <ElCol :xs="24" :md="12">
        <ElCard class="content-card">
          <template #header>
            <div class="page-header">
              <div>
                <h2>分类</h2>
                <p>维护文章所属分类。</p>
              </div>
            </div>
          </template>
          <ElForm class="inline-form" @submit.prevent="addCategory">
            <ElInput v-model="newCategory" placeholder="新分类名称" />
            <ElButton type="primary" @click="addCategory">添加</ElButton>
          </ElForm>
          <ElTable v-loading="loading" :data="categories" row-key="id">
            <ElTableColumn label="名称">
              <template #default="{ row }">
                <ElInput v-if="editingCategoryId === row.id" v-model="editingName" />
                <span v-else>{{ row.name }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="180">
              <template #default="{ row }">
                <ElSpace>
                  <template v-if="editingCategoryId === row.id">
                    <ElButton link type="primary" @click="saveCategory(row.id)">保存</ElButton>
                    <ElButton link @click="cancelEdit">取消</ElButton>
                  </template>
                  <template v-else>
                    <ElButton link type="primary" @click="startEdit(row.id, row.name)">重命名</ElButton>
                    <ElButton link type="danger" @click="removeCategory(row.id)">停用</ElButton>
                  </template>
                </ElSpace>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElCol>

      <ElCol :xs="24" :md="12">
        <ElCard class="content-card">
          <template #header>
            <div class="page-header">
              <div>
                <h2>标签</h2>
                <p>维护文章标签体系。</p>
              </div>
            </div>
          </template>
          <ElForm class="inline-form" @submit.prevent="addTag">
            <ElInput v-model="newTag" placeholder="新标签名称" />
            <ElButton type="primary" @click="addTag">添加</ElButton>
          </ElForm>
          <ElTable v-loading="loading" :data="tags" row-key="id">
            <ElTableColumn label="名称">
              <template #default="{ row }">
                <ElInput v-if="editingTagId === row.id" v-model="editingName" />
                <span v-else>{{ row.name }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="180">
              <template #default="{ row }">
                <ElSpace>
                  <template v-if="editingTagId === row.id">
                    <ElButton link type="primary" @click="saveTag(row.id)">保存</ElButton>
                    <ElButton link @click="cancelEdit">取消</ElButton>
                  </template>
                  <template v-else>
                    <ElButton link type="primary" @click="startEditTag(row.id, row.name)">重命名</ElButton>
                    <ElButton link type="danger" @click="removeTag(row.id)">停用</ElButton>
                  </template>
                </ElSpace>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElCol>
    </ElRow>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import type { BlogCategory, BlogTag } from '@mindora/types'
  import {
    createCategory,
    createTag,
    deleteCategory,
    deleteTag,
    listCategories,
    listTags,
    updateCategory,
    updateTag
  } from '@/api/content'

  defineOptions({ name: 'ContentTaxonomy' })

  const loading = ref(false)
  const categories = ref<BlogCategory[]>([])
  const tags = ref<BlogTag[]>([])
  const newCategory = ref('')
  const newTag = ref('')
  const editingCategoryId = ref('')
  const editingTagId = ref('')
  const editingName = ref('')

  const loadData = async () => {
    loading.value = true
    try {
      const [categoryList, tagList] = await Promise.all([listCategories(), listTags()])
      categories.value = categoryList
      tags.value = tagList
    } finally {
      loading.value = false
    }
  }

  const addCategory = async () => {
    const name = newCategory.value.trim()
    if (!name) return
    categories.value.push(await createCategory(name))
    newCategory.value = ''
    ElMessage.success('分类已添加')
  }

  const addTag = async () => {
    const name = newTag.value.trim()
    if (!name) return
    tags.value.push(await createTag(name))
    newTag.value = ''
    ElMessage.success('标签已添加')
  }

  const startEdit = (id: string, name: string) => {
    editingCategoryId.value = id
    editingTagId.value = ''
    editingName.value = name
  }

  const startEditTag = (id: string, name: string) => {
    editingTagId.value = id
    editingCategoryId.value = ''
    editingName.value = name
  }

  const cancelEdit = () => {
    editingCategoryId.value = ''
    editingTagId.value = ''
    editingName.value = ''
  }

  const saveCategory = async (id: string) => {
    const category = await updateCategory(id, editingName.value.trim())
    categories.value = categories.value.map((item) => (item.id === id ? category : item))
    cancelEdit()
    ElMessage.success('分类已更新')
  }

  const saveTag = async (id: string) => {
    const tag = await updateTag(id, editingName.value.trim())
    tags.value = tags.value.map((item) => (item.id === id ? tag : item))
    cancelEdit()
    ElMessage.success('标签已更新')
  }

  const removeCategory = async (id: string) => {
    await ElMessageBox.confirm('确定停用该分类吗？', '停用分类')
    await deleteCategory(id)
    categories.value = categories.value.filter((item) => item.id !== id)
    ElMessage.success('分类已停用')
  }

  const removeTag = async (id: string) => {
    await ElMessageBox.confirm('确定停用该标签吗？', '停用标签')
    await deleteTag(id)
    tags.value = tags.value.filter((item) => item.id !== id)
    ElMessage.success('标签已停用')
  }

  onMounted(loadData)
</script>

<style scoped>
  .content-page {
    padding: 20px;
  }

  .content-card {
    margin-bottom: 16px;
    border-radius: 8px;
  }

  .page-header h2 {
    margin: 0 0 6px;
    font-size: 20px;
  }

  .page-header p {
    margin: 0;
    color: var(--art-gray-600);
  }

  .inline-form {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 10px;
    margin-bottom: 16px;
  }
</style>
