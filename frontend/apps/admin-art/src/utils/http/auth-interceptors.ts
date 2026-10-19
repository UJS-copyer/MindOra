/**
 * 认证相关的 axios 拦截器逻辑（与 Vue 解耦，便于单测）。
 *
 * <p>三条职责：
 * 1. 请求前注入 `Authorization: Bearer <access token>`；
 * 2. 收到 401 `token_expired` 时单飞刷新并**重放一次**原请求；
 * 3. 其余 401 交给 `onSessionLost`（清会话、提示、回登录页）。
 *
 * <p>登录/注册自身的 401（如 `invalid_credentials`）不在这里处理：那是「这次凭据不对」，
 * 不是「会话失效」，交给登录页展示。
 */
import type { AxiosError, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

const CREDENTIAL_ENTRY_PATHS = ['/api/v1/auth/login', '/api/v1/auth/register']

export interface AuthInterceptorDeps {
  getToken(): string

  refreshToken(): Promise<string>

  /** 用同一个 axios 实例重放原请求（此时 config 已带新 token）。 */
  replay(config: AxiosRequestConfig): Promise<AxiosResponse>

  /** 需要清理登录态时调用；`code` 为后端返回的业务错误码。 */
  onSessionLost(code: string): void
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  __authRetried?: boolean
}

interface TokenHeaderCarrier {
  headers: {
    set(name: string, value: string): unknown
  }
}

/** 给请求头写入 Bearer 令牌（测试可直接调用）。 */
export function injectBearerToken(
  config: TokenHeaderCarrier | undefined,
  token: string
): void {
  if (!config || !token) return
  config.headers.set('Authorization', `Bearer ${token}`)
}

function isCredentialEntryRequest(url: string | undefined): boolean {
  if (!url) return false
  return CREDENTIAL_ENTRY_PATHS.some((path) => url.includes(path))
}

/**
 * 处理认证失败。
 *
 * <p>成功重放时返回新响应；其余情况把原始错误继续抛出，交给上层统一转成 HttpError。
 */
export async function handleAuthError(
  error: unknown,
  deps: AuthInterceptorDeps
): Promise<AxiosResponse> {
  const axiosError = error as AxiosError<{ code?: string }>
  const status = axiosError.response?.status
  const code = axiosError.response?.data?.code
  const config = axiosError.config as RetriableRequestConfig | undefined

  if (status === 401 && !isCredentialEntryRequest(config?.url)) {
    if (code === 'token_expired' && config && !config.__authRetried) {
      config.__authRetried = true
      try {
        const token = await deps.refreshToken()
        injectBearerToken(config, token)
        return await deps.replay(config)
      } catch {
        deps.onSessionLost(code)
        throw error
      }
    }
    deps.onSessionLost(code ?? 'token_invalid')
  }

  throw error
}

/** 请求拦截器：每次请求都取当前内存令牌，保证续期后的请求立即生效。 */
export function authRequestInterceptor(
  config: InternalAxiosRequestConfig,
  deps: Pick<AuthInterceptorDeps, 'getToken'>
): InternalAxiosRequestConfig {
  injectBearerToken(config, deps.getToken())
  return config
}
