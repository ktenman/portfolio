import { beforeEach, describe, expect, it, vi } from 'vitest'
import { forgetReload, reloadOnce } from './dom'

describe('reloadOnce', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(window.location.reload).mockClear()
  })

  it('reloads the page when a part of the running build fails to load', () => {
    reloadOnce()
    expect(window.location.reload).toHaveBeenCalledTimes(1)
  })

  it('reloads only once until the app starts', () => {
    reloadOnce()
    reloadOnce()
    expect(window.location.reload).toHaveBeenCalledTimes(1)
  })

  it('reloads again after the app has started', () => {
    reloadOnce()
    forgetReload()
    reloadOnce()
    expect(window.location.reload).toHaveBeenCalledTimes(2)
  })
})
