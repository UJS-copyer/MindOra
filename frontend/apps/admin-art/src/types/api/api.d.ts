/**
 * API 类型兼容层
 *
 * 这里保留 `Api.*` 命名空间，只是为了让迁移后的页面少改代码。
 * 真正的数据结构以 `@mindora/types` 里生成的 OpenAPI 类型为准。
 */

import type { components } from '@mindora/types'

type PickRequired<T, K extends keyof T> = Required<Pick<T, K>> & Omit<T, K>

type AuthRequest = components['schemas']['AuthRequest']
type AuthView = components['schemas']['AuthView']
type AdminUserView = components['schemas']['AdminUserView']
type UserRow = components['schemas']['UserRow']
type RoleRow = components['schemas']['RoleRow']
type PermissionRow = components['schemas']['PermissionRow']
type PageResultUserRow = components['schemas']['PageResultUserRow']
type PageResultRoleRow = components['schemas']['PageResultRoleRow']

declare global {
  namespace Api {
    namespace Common {
      interface PaginationParams {
        current: number
        size: number
        total: number
      }

      type CommonSearchParams = Pick<PaginationParams, 'current' | 'size'>

      interface PaginatedResponse<T = unknown> {
        records: T[]
        current: number
        size: number
        total: number
      }

      type EnableStatus = '1' | '2'
    }

    namespace Auth {
      type LoginParams = AuthRequest
      type LoginResponse = AuthView & { refreshToken?: string }
      type UserInfo = AdminUserView
    }

    namespace SystemManage {
      type UserList = Api.Common.PaginatedResponse<UserListItem>
      type UserListItem = PickRequired<
        UserRow,
        'id' | 'userName' | 'userGender' | 'userPhone' | 'userEmail' | 'status' | 'userRoles' | 'createTime' | 'updateTime'
      > & {
        avatar?: string
        nickName?: string
        createBy?: string
        updateBy?: string
      }

      type UserRequest = components['schemas']['UserRequest']

      type UserSearchParams = Partial<
        Pick<UserListItem, 'id' | 'userName' | 'userGender' | 'userPhone' | 'userEmail' | 'status'> &
          Api.Common.CommonSearchParams
      >

      type RoleList = Api.Common.PaginatedResponse<RoleListItem>
      type RoleListItem = PickRequired<
        RoleRow,
        'roleId' | 'roleName' | 'roleCode' | 'description' | 'enabled' | 'createTime'
      > & {
        permissions?: string[]
      }

      type RoleRequest = components['schemas']['RoleRequest']

      type PermissionListItem = PickRequired<PermissionRow, 'code'> & {
        description?: string
      }

      type RoleSearchParams = Partial<
        Pick<RoleListItem, 'roleId' | 'roleName' | 'roleCode' | 'description' | 'enabled'> &
          Api.Common.CommonSearchParams & {
            startTime: string | null
            endTime: string | null
          }
      >
    }
  }
}

export {}
