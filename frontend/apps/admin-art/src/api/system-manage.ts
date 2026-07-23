import request from '@/utils/http'
import { AppRouteRecord } from '@/types/router'
import type { components } from '@mindora/types'

type AdminMenu = components['schemas']['MenuView']

// 获取用户列表
export function fetchGetUserList(params: Api.SystemManage.UserSearchParams) {
  return request.get<Api.SystemManage.UserList>({
    url: '/api/v1/admin/users',
    params
  })
}

export function fetchCreateUser(data: Api.SystemManage.UserRequest) {
  return request.post<Api.SystemManage.UserListItem>({
    url: '/api/v1/admin/users',
    data
  })
}

export function fetchUpdateUser(id: string, data: Api.SystemManage.UserRequest) {
  return request.put<Api.SystemManage.UserListItem>({
    url: `/api/v1/admin/users/${id}`,
    data
  })
}

export function fetchDeleteUser(id: string) {
  return request.del<void>({
    url: `/api/v1/admin/users/${id}`
  })
}

// 获取角色列表
export function fetchGetRoleList(params: Api.SystemManage.RoleSearchParams) {
  return request.get<Api.SystemManage.RoleList>({
    url: '/api/v1/admin/roles',
    params
  })
}

export function fetchCreateRole(data: Api.SystemManage.RoleRequest) {
  return request.post<Api.SystemManage.RoleListItem>({
    url: '/api/v1/admin/roles',
    data
  })
}

export function fetchUpdateRole(id: string, data: Api.SystemManage.RoleRequest) {
  return request.put<Api.SystemManage.RoleListItem>({
    url: `/api/v1/admin/roles/${id}`,
    data
  })
}

export function fetchDeleteRole(id: string) {
  return request.del<void>({
    url: `/api/v1/admin/roles/${id}`
  })
}

export function fetchGetRolePermissions(id: string) {
  return request.get<string[]>({
    url: `/api/v1/admin/roles/${id}/permissions`
  })
}

export function fetchUpdateRolePermissions(id: string, permissions: string[]) {
  return request.put<string[]>({
    url: `/api/v1/admin/roles/${id}/permissions`,
    data: { permissions }
  })
}

export function fetchGetPermissionList() {
  return request.get<Api.SystemManage.PermissionListItem[]>({
    url: '/api/v1/admin/permissions'
  })
}

// 获取菜单列表
export function fetchGetMenuList() {
  return request
    .get<AdminMenu[]>({
      url: '/api/v1/admin/menus'
    })
    .then((menus) => menus.map(toAppRoute))
}

function toAppRoute(menu: AdminMenu): AppRouteRecord {
  return {
    path: menu.path || '',
    name: menu.name,
    component: menu.component,
    meta: {
      title: menu.meta?.title || menu.name || '',
      ...(menu.meta?.icon ? { icon: menu.meta.icon } : {}),
      ...(menu.meta?.roles ? { roles: menu.meta.roles } : {}),
      ...(menu.meta?.authList ? { authList: menu.meta.authList } : {})
    },
    ...(menu.children?.length
      ? {
          children: menu.children.map((child) =>
            toAppRoute({ ...child, children: [] } as AdminMenu)
          )
        }
      : {})
  } as AppRouteRecord
}
