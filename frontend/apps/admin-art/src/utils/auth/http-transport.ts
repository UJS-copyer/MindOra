/**
 * 会话 transport：用**裸 axios**（不装业务拦截器）访问认证端点。
 *
 * <p>为什么单独一个实例：refresh/logout 自身若走业务拦截器，会形成
 * 「401 → 刷新 → 又 401 → 再刷新」的递归；这里必须绕开。
 *
 * <p>Cookie 依赖同源：本地由 vite 的 `/api` 代理转发，生产由同域反代，浏览器自动携带
 * HttpOnly 的 `mindora_rt`，因此无需 `withCredentials`。
 */
import axios from 'axios'
import { createAuthSession, type SessionTransport } from './session'

const { VITE_API_BASE_URL, VITE_API_URL } = import.meta.env

const AUTH_BASE_URL = VITE_API_BASE_URL || VITE_API_URL || ''
const REFRESH_URL = '/api/v1/auth/refresh'
const LOGOUT_URL = '/api/v1/auth/logout'

interface AuthEnvelope<T> {
  code: string
  data?: T
}

const http = axios.create({
  baseURL: AUTH_BASE_URL,
  timeout: 10000
})

const transport: SessionTransport = {
  async refresh(): Promise<string> {
    const response = await http.post<AuthEnvelope<{ accessToken?: string }>>(REFRESH_URL)
    const token = response.data?.data?.accessToken
    if (!token) {
      throw new Error('Refresh response did not contain an access token')
    }
    return token
  },

  async logout(): Promise<void> {
    await http.post(LOGOUT_URL)
  }
}

export const session = createAuthSession(transport)

/** 启动时静默登录（守卫与 main.ts 共用同一个 Promise）。 */
export const ensureSessionBootstrapped = (): Promise<boolean> => session.bootstrap()

export const getAccessToken = (): string => session.getAccessToken()

export const setAccessToken = (token: string): void => session.setAccessToken(token)

export const clearSession = (): void => session.clear()

export const isAuthenticated = (): boolean => session.isAuthenticated()

export const refreshAccessToken = (): Promise<string> => session.refresh()

/** 通知后端结束会话并清空内存令牌（失败也会清空）。 */
export const logoutSession = (): Promise<void> => session.logout()
