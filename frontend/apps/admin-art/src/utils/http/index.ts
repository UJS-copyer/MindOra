/**
 * HTTP 请求封装模块
 * 基于 Axios 封装的 HTTP 请求工具，提供统一的请求/响应处理
 *
 * ## 主要功能
 *
 * - 请求/响应拦截器（Bearer 令牌注入、401 单飞续期与重放、统一错误处理）
 * - 会话失效时清理内存令牌并回到登录页（带防抖，避免并发请求重复提示）
 * - 请求失败自动重试（可配置）
 * - 统一的成功/错误消息提示
 * - 支持 GET/POST/PUT/DELETE 等常用方法
 *
 * 登录态细节见 `docs/design-12-identity-and-token-lifecycle.md` §8：
 * Access Token 只存内存，刷新页面靠 `/auth/refresh` 静默恢复。
 *
 * @module utils/http
 */

import axios, { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/store/modules/user'
import { ApiStatus } from './status'
import { HttpError, handleError, showError, showSuccess } from './error'
import { authRequestInterceptor, handleAuthError } from './auth-interceptors'
import {
  clearSession,
  getAccessToken,
  refreshAccessToken
} from '@/utils/auth/http-transport'
import { $t } from '@/locales'
import { BaseResponse } from '@/types'

/** 请求配置常量 */
const REQUEST_TIMEOUT = 15000
const LOGOUT_DELAY = 300
const MAX_RETRIES = 0
const RETRY_DELAY = 1000
const UNAUTHORIZED_DEBOUNCE_TIME = 3000

/** 401 提示防抖状态：并发请求同时失效时只提示/跳转一次 */
let isSessionLostHandled = false
let sessionLostTimer: NodeJS.Timeout | null = null

/** 扩展 AxiosRequestConfig */
interface ExtendedAxiosRequestConfig extends AxiosRequestConfig {
  showErrorMessage?: boolean
  showSuccessMessage?: boolean
}

const { VITE_API_BASE_URL, VITE_API_URL, VITE_WITH_CREDENTIALS } = import.meta.env

/** Axios实例 */
const axiosInstance = axios.create({
  timeout: REQUEST_TIMEOUT,
  baseURL: VITE_API_BASE_URL || VITE_API_URL,
  withCredentials: VITE_WITH_CREDENTIALS === 'true',
  validateStatus: (status) => status >= 200 && status < 300,
  transformResponse: [
    (data, headers) => {
      const contentType = String(headers['content-type'] ?? '')
      if (contentType.includes('application/json')) {
        try {
          return JSON.parse(data)
        } catch {
          return data
        }
      }
      return data
    }
  ]
})

/** 请求拦截器：注入当前内存令牌（续期后的新令牌立即生效） */
axiosInstance.interceptors.request.use(
  (request: InternalAxiosRequestConfig) =>
    authRequestInterceptor(request, { getToken: getAccessToken }),
  (error) => {
    showError(createHttpError($t('httpMsg.requestConfigError'), ApiStatus.error))
    return Promise.reject(error)
  }
)

/** 响应拦截器 */
axiosInstance.interceptors.response.use(
  (response: AxiosResponse<BaseResponse>) => {
    const { code, msg, message } = response.data
    const responseMessage = msg || message || $t('httpMsg.requestFailed')
    if (code === 'success' || code === ApiStatus.success) return response
    throw createHttpError(responseMessage, response.status || ApiStatus.error)
  },
  async (error) => {
    try {
      // 401 token_expired：单飞刷新并重放一次；其余 401 触发会话失效
      return await handleAuthError(error, {
        getToken: getAccessToken,
        refreshToken: refreshAccessToken,
        replay: (config) => axiosInstance.request(config),
        onSessionLost: handleSessionLost
      })
    } catch {
      return Promise.reject(handleError(error))
    }
  }
)

/** 统一创建HttpError */
function createHttpError(message: string, code: number) {
  return new HttpError(message, code)
}

/**
 * 会话失效：清空内存令牌 + 提示一次 + 回登录页。
 * 并发请求同时 401 时用 3 秒防抖避免重复弹窗与重复跳转。
 */
function handleSessionLost(code: string): void {
  if (isSessionLostHandled) return
  isSessionLostHandled = true

  clearSession()
  const message =
    code === 'account_disabled'
      ? $t('httpMsg.accountDisabled')
      : $t('httpMsg.sessionExpired')
  showError(createHttpError(message, ApiStatus.unauthorized), true)

  sessionLostTimer = setTimeout(() => {
    isSessionLostHandled = false
    if (sessionLostTimer) clearTimeout(sessionLostTimer)
    sessionLostTimer = null
  }, UNAUTHORIZED_DEBOUNCE_TIME)

  setTimeout(() => {
    // 清理本地状态并回登录页（logOut 内部会 best-effort 通知后端撤销刷新令牌）
    useUserStore().logOut()
  }, LOGOUT_DELAY)
}

/** 是否需要重试 */
function shouldRetry(statusCode: number) {
  return [
    ApiStatus.requestTimeout,
    ApiStatus.internalServerError,
    ApiStatus.badGateway,
    ApiStatus.serviceUnavailable,
    ApiStatus.gatewayTimeout
  ].includes(statusCode)
}

/** 请求重试逻辑 */
async function retryRequest<T>(
  config: ExtendedAxiosRequestConfig,
  retries: number = MAX_RETRIES
): Promise<T> {
  try {
    return await request<T>(config)
  } catch (error) {
    if (retries > 0 && error instanceof HttpError && shouldRetry(error.code)) {
      await delay(RETRY_DELAY)
      return retryRequest<T>(config, retries - 1)
    }
    throw error
  }
}

/** 延迟函数 */
function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 请求函数 */
async function request<T = any>(config: ExtendedAxiosRequestConfig): Promise<T> {
  // POST | PUT 参数自动填充
  if (
    ['POST', 'PUT'].includes(config.method?.toUpperCase() || '') &&
    config.params &&
    !config.data
  ) {
    config.data = config.params
    config.params = undefined
  }

  try {
    const res = await axiosInstance.request<BaseResponse<T>>(config)

    // 显示成功消息
    if (config.showSuccessMessage && res.data.msg) {
      showSuccess(res.data.msg)
    }

    return res.data.data as T
  } catch (error) {
    // 401 已由会话失效逻辑统一提示；其余错误按配置提示
    if (error instanceof HttpError && error.code !== ApiStatus.unauthorized) {
      const showMsg = config.showErrorMessage !== false
      showError(error, showMsg)
    }
    return Promise.reject(error)
  }
}

/** API方法集合 */
const api = {
  get<T>(config: ExtendedAxiosRequestConfig) {
    return retryRequest<T>({ ...config, method: 'GET' })
  },
  post<T>(config: ExtendedAxiosRequestConfig) {
    return retryRequest<T>({ ...config, method: 'POST' })
  },
  put<T>(config: ExtendedAxiosRequestConfig) {
    return retryRequest<T>({ ...config, method: 'PUT' })
  },
  del<T>(config: ExtendedAxiosRequestConfig) {
    return retryRequest<T>({ ...config, method: 'DELETE' })
  },
  request<T>(config: ExtendedAxiosRequestConfig) {
    return retryRequest<T>(config)
  }
}

export default api
