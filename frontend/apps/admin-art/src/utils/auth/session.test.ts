import { describe, expect, it, vi } from 'vitest'
import { createAuthSession, type SessionTransport } from './session'

function transport(overrides: Partial<SessionTransport> = {}): SessionTransport {
  return {
    refresh: vi.fn(async () => 'token-1'),
    logout: vi.fn(async () => undefined),
    ...overrides
  }
}

describe('createAuthSession', () => {
  it('bootstraps a token from the refresh cookie', async () => {
    const session = createAuthSession(transport())

    await expect(session.bootstrap()).resolves.toBe(true)

    expect(session.isAuthenticated()).toBe(true)
    expect(session.getAccessToken()).toBe('token-1')
  })

  it('clears the token and returns false when bootstrap fails', async () => {
    const session = createAuthSession(
      transport({ refresh: vi.fn(async () => Promise.reject(new Error('no cookie'))) })
    )

    await expect(session.bootstrap()).resolves.toBe(false)

    expect(session.isAuthenticated()).toBe(false)
    expect(session.getAccessToken()).toBe('')
  })

  it('shares one refresh request between concurrent callers', async () => {
    let resolveRefresh: (token: string) => void = () => undefined
    const refresh = vi.fn(
      () =>
        new Promise<string>((resolve) => {
          resolveRefresh = resolve
        })
    )
    const session = createAuthSession(transport({ refresh }))

    const first = session.refresh()
    const second = session.refresh()
    resolveRefresh('token-shared')

    await expect(Promise.all([first, second])).resolves.toEqual(['token-shared', 'token-shared'])
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('clears the token when a refresh fails', async () => {
    const session = createAuthSession(
      transport({ refresh: vi.fn(async () => Promise.reject(new Error('expired'))) })
    )
    session.setAccessToken('stale')

    await expect(session.refresh()).rejects.toThrow('expired')

    expect(session.getAccessToken()).toBe('')
  })

  it('clears local state even if the logout request fails', async () => {
    const session = createAuthSession(
      transport({ logout: vi.fn(async () => Promise.reject(new Error('offline'))) })
    )
    session.setAccessToken('token-1')

    await session.logout()

    expect(session.isAuthenticated()).toBe(false)
  })
})
