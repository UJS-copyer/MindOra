import request from '@/utils/http'
import type { components } from '@mindora/types'

type AuthRequest = components['schemas']['AuthRequest']
type AuthView = components['schemas']['AuthView']
type AdminUserView = components['schemas']['AdminUserView']

/**
 * 登录
 * @param params 登录参数
 * @returns 登录响应
 */
export function fetchLogin(params: AuthRequest) {
  return request.post<AuthView>({
    url: '/api/v1/auth/login',
    params
    // showSuccessMessage: true // 显示成功消息
    // showErrorMessage: false // 不显示错误消息
  })
}

/**
 * 获取用户信息
 * @returns 用户信息
 */
export function fetchGetUserInfo() {
  return request.get<AdminUserView>({
    url: '/api/v1/admin/me'
  })
}
