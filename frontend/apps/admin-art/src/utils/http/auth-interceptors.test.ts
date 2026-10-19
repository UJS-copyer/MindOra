import { describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import { handleAuthError, injectBearerToken, type AuthInterceptorDeps } from './auth-interceptors'

function headers() {
  const values = new Map<string, string>()
  const instance = {
    set: (name: string, value: string) => {
      values.set(name, value)
      return instance
    },
    get: (name: string) => values.get(name)
  }
  return instance
}

function unauthorizedError(url: string, code: string) {
  const config = { url, headers: headers(), __authRetried: false }
  return {
    config,
    response: { status: 401, data: { code } }
  }
}

function deps(overrides: Partial<AuthInterceptorDeps> = {}): AuthInterceptorDeps {
  return {
    getToken: () => 'stale',
    refreshToken: vi.fn(async () => 'fresh'),
    replay: vi.fn(async () => ({ status: 200, data: { code: 'success' } }) as AxiosResponse),
    onSessionLost: vi.fn(),
    ...overrides
  }
}

describe('injectBearerToken', () => {
  it('writes a Bearer header and skips empty tokens', () => {
    const config = { headers: headers() }

    injectBearerToken(config, 'abc')
    expect(config.headers.get('Authorization')).toBe('Bearer abc')

    const untouched = { headers: headers() }
    injectBearerToken(untouched, '')
    expect(untouched.headers.get('Authorization')).toBeUndefined()
  })
})

describe('handleAuthError', () => {
  it('refreshes once and replays the original request on token_expired', async () => {
    const error = unauthorizedError('/api/v1/admin/me', 'token_expired')
    const dependencies = deps()

    const response = await handleAuthError(error, dependencies)

    expect(response.status).toBe(200)
    expect(dependencies.refreshToken).toHaveBeenCalledTimes(1)
    expect(dependencies.replay).toHaveBeenCalledTimes(1)
    expect(dependencies.replay).toHaveBeenCalledWith(error.config)
    expect(error.config.headers.get('Authorization')).toBe('Bearer fresh')
    expect(error.config.__authRetried).toBe(true)
    expect(dependencies.onSessionLost).not.toHaveBeenCalled()
  })

  it('does not retry a second time when the replay also expires', async () => {
    const error = unauthorizedError('/api/v1/admin/me', 'token_expired')
    const dependencies = deps()

    await handleAuthError(error, dependencies)
    await expect(handleAuthError(error, dependencies)).rejects.toBe(error)

    expect(dependencies.refreshToken).toHaveBeenCalledTimes(1)
    expect(dependencies.replay).toHaveBeenCalledTimes(1)
    expect(dependencies.onSessionLost).toHaveBeenCalledWith('token_expired')
  })

  it('treats other 401 codes as session loss without replaying', async () => {
    const error = unauthorizedError('/api/v1/admin/me', 'token_revoked')
    const dependencies = deps()

    await expect(handleAuthError(error, dependencies)).rejects.toBe(error)

    expect(dependencies.onSessionLost).toHaveBeenCalledWith('token_revoked')
    expect(dependencies.replay).not.toHaveBeenCalled()
  })

  it('leaves credential errors to the login page', async () => {
    const error = unauthorizedError('/api/v1/auth/login', 'invalid_credentials')
    const dependencies = deps()

    await expect(handleAuthError(error, dependencies)).rejects.toBe(error)

    expect(dependencies.onSessionLost).not.toHaveBeenCalled()
    expect(dependencies.refreshToken).not.toHaveBeenCalled()
  })

  it('does not clear the session on forbidden responses', async () => {
    const error = {
      config: { url: '/api/v1/admin/me', headers: headers() },
      response: { status: 403, data: { code: 'access_denied' } }
    }
    const dependencies = deps()

    await expect(handleAuthError(error, dependencies)).rejects.toBe(error)

    expect(dependencies.onSessionLost).not.toHaveBeenCalled()
    expect(dependencies.refreshToken).not.toHaveBeenCalled()
  })
})
