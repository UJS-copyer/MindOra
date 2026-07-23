<template>
  <ElDialog
    v-model="visible"
    title="角色权限"
    width="560px"
    align-center
    class="el-dialog-border"
    @close="handleClose"
  >
    <ElSkeleton v-if="loading" :rows="6" animated />

    <ElCheckboxGroup v-else v-model="selectedPermissions" class="permission-list">
      <div
        v-for="group in permissionGroups"
        :key="group.name"
        class="permission-group"
      >
        <div class="permission-group__title">{{ group.name }}</div>
        <ElSpace wrap>
          <ElCheckbox
            v-for="permission in group.items"
            :key="permission.code"
            :value="permission.code"
            border
          >
            <span>{{ permission.code }}</span>
            <span v-if="permission.description" class="permission-desc">
              {{ permission.description }}
            </span>
          </ElCheckbox>
        </ElSpace>
      </div>
    </ElCheckboxGroup>

    <template #footer>
      <ElButton @click="toggleSelectAll">
        {{ isSelectAll ? '取消全选' : '全部选择' }}
      </ElButton>
      <ElButton type="primary" :loading="saving" @click="savePermission">保存</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import {
    fetchGetPermissionList,
    fetchGetRolePermissions,
    fetchUpdateRolePermissions
  } from '@/api/system-manage'

  type RoleListItem = Api.SystemManage.RoleListItem
  type PermissionItem = Api.SystemManage.PermissionListItem

  interface Props {
    modelValue: boolean
    roleData?: RoleListItem
  }

  interface Emits {
    (e: 'update:modelValue', value: boolean): void
    (e: 'success'): void
  }

  const props = withDefaults(defineProps<Props>(), {
    modelValue: false,
    roleData: undefined
  })

  const emit = defineEmits<Emits>()

  const loading = ref(false)
  const saving = ref(false)
  const permissionList = ref<PermissionItem[]>([])
  const selectedPermissions = ref<string[]>([])

  const visible = computed({
    get: () => props.modelValue,
    set: (value) => emit('update:modelValue', value)
  })

  const permissionGroups = computed(() => {
    const groups = new Map<string, PermissionItem[]>()
    permissionList.value.forEach((permission) => {
      const prefix = permission.code.split(':')[0] || 'other'
      if (!groups.has(prefix)) groups.set(prefix, [])
      groups.get(prefix)?.push(permission)
    })
    return Array.from(groups.entries()).map(([name, items]) => ({ name, items }))
  })

  const isSelectAll = computed(() => {
    return (
      permissionList.value.length > 0 &&
      selectedPermissions.value.length === permissionList.value.length
    )
  })

  const loadPermissions = async () => {
    if (!props.roleData?.roleId) return

    loading.value = true
    try {
      const [permissions, rolePermissions] = await Promise.all([
        fetchGetPermissionList(),
        fetchGetRolePermissions(props.roleData.roleId)
      ])
      permissionList.value = permissions
      selectedPermissions.value = rolePermissions
    } finally {
      loading.value = false
    }
  }

  watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal) {
        loadPermissions()
      }
    }
  )

  const handleClose = () => {
    visible.value = false
    selectedPermissions.value = []
  }

  const savePermission = async () => {
    if (!props.roleData?.roleId) return

    saving.value = true
    try {
      await fetchUpdateRolePermissions(props.roleData.roleId, selectedPermissions.value)
      ElMessage.success('权限保存成功')
      emit('success')
      handleClose()
    } finally {
      saving.value = false
    }
  }

  const toggleSelectAll = () => {
    selectedPermissions.value = isSelectAll.value
      ? []
      : permissionList.value.map((permission) => permission.code)
  }
</script>

<style scoped>
  .permission-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .permission-group__title {
    margin-bottom: 10px;
    font-weight: 600;
    color: var(--art-text-gray-800);
  }

  .permission-desc {
    margin-left: 6px;
    color: var(--art-text-gray-500);
  }
</style>
