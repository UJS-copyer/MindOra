/**
 * 内存中的登录会话（design-12 §8）。
 *
 * <p>Access Token 只保存在内存：刷新页面后由 bootstrap 用 HttpOnly 刷新令牌 Cookie
 * 重新换一枚；不写 localStorage，避免 XSS 直接读走长期令牌。
 *
 * <p>本模块不依赖 Vue / router，transport 由调用方注入，便于在 Node 里单测。
 */

export interface SessionTransport {
  /** 用 Cookie 换新的 Access Token（失败时 reject）。 */
  refresh(): Promise<string>
  /** 通知后端结束会话（失败可忽略）。 */
  logout(): Promise<void>
}

export interface AuthSession {
  getAccessToken(): string

  setAccessToken(token: string): void

  clear(): void

  isAuthenticated(): boolean

  /** 页面启动时的静默登录：成功返回 true；失败清空令牌并返回 false。 */
  bootstrap(): Promise<boolean>

  /** 单飞刷新：并发调用共享同一个请求。 */
  refresh(): Promise<string>

  logout(): Promise<void>
}

export function createAuthSession(transport: SessionTransport): AuthSession {
  let accessToken = ''
  let bootstrapPromise: Promise<boolean> | null = null
  let refreshPromise: Promise<string> | null = null

  const refresh = (): Promise<string> => {
    if (!refreshPromise) {
      refreshPromise = transport
        .refresh()
        .then((token) => {
          accessToken = token
          return token
        })
        .catch((error: unknown) => {
          accessToken = ''
          throw error
        })
        .finally(() => {
          refreshPromise = null
        })
    }
    return refreshPromise
  }

  const bootstrap = (): Promise<boolean> => {
    if (!bootstrapPromise) {
      bootstrapPromise = refresh()
        .then(() => true)
        .catch(() => false)
    }
    return bootstrapPromise
  }

  const logout = async (): Promise<void> => {
    try {
      await transport.logout()
    } catch {
      // 登出是幂等动作：后端失败/无 Cookie 也要清掉本地状态
    } finally {
      accessToken = ''
    }
  }

  return {
    getAccessToken: () => accessToken,
    setAccessToken: (token: string) => {
      accessToken = token
    },
    clear: () => {
      accessToken = ''
    },
    isAuthenticated: () => accessToken !== '',
    bootstrap,
    refresh,
    logout
  }
}
