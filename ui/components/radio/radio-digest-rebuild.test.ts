import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { h } from 'vue'
import { type DigestBuild, DigestShow } from '../../models/generated/domain-models'
import { ApiError } from '../../models/api-error'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioDigestRebuild from './radio-digest-rebuild.vue'
import { digestBuild, digestFailure } from './radio-digest-fixture'

const { confirm, toast } = vi.hoisted(() => ({
  confirm: vi.fn(),
  toast: { error: vi.fn() },
}))

vi.mock('../../services/api', () => ({
  radioService: { digestBuild: vi.fn(), rebuildDigest: vi.fn() },
}))
vi.mock('../../composables/use-confirm', () => ({ useConfirm: () => ({ confirm }) }))
vi.mock('../../composables/use-toast', () => ({ useToast: () => toast }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const BUTTON = '[data-testid="radio-digest-rebuild"]'

const mount = async ({
  show = DigestShow.DAY,
  found = null,
}: { show?: DigestShow; found?: DigestBuild | null } = {}) => {
  vi.mocked(radioService.digestBuild).mockResolvedValue(found)
  const wrapper = renderWithProviders({ render: () => h(RadioDigestRebuild, { show }) })
  await flushPromises()
  return wrapper
}

const click = async (show = DigestShow.DAY) => {
  const wrapper = await mount({ show })
  await wrapper.get(BUTTON).trigger('click')
  await flushPromises()
  return wrapper
}

const refuse = async (error: Error) => {
  vi.mocked(radioService.rebuildDigest).mockRejectedValue(error)
  await click()
}

describe('RadioDigestRebuild', () => {
  beforeEach(() => {
    confirm.mockReset().mockResolvedValue(true)
    toast.error.mockReset()
    vi.mocked(radioService.digestBuild).mockReset()
    vi.mocked(radioService.rebuildDigest).mockReset().mockResolvedValue()
  })

  it('should offer to rebuild the day from the last 24 hours', async () => {
    expect((await mount()).get(BUTTON).text()).toBe('Rebuild from the last 24 hours')
  })

  it('should ask before it rebuilds', async () => {
    await click()
    expect(confirm).toHaveBeenCalledWith({
      title: 'Rebuild Day in Radio?',
      message:
        'The stories are chosen again from the last 24 hours. It takes a few minutes, and a digest made today is replaced.',
      confirmText: 'Rebuild',
      cancelText: 'Cancel',
      variant: 'primary',
    })
  })

  it('should not rebuild when the user cancels', async () => {
    confirm.mockResolvedValue(false)
    await click()
    expect(radioService.rebuildDigest).not.toHaveBeenCalled()
  })

  it('should rebuild its show once the user confirms', async () => {
    await click(DigestShow.WEEK)
    expect(radioService.rebuildDigest).toHaveBeenCalledWith(DigestShow.WEEK)
  })

  it('should be disabled while a digest builds', async () => {
    const wrapper = await mount({ found: digestBuild() })
    expect(wrapper.get(BUTTON).attributes('disabled')).toBeDefined()
  })

  it('should stay enabled on a failed build', async () => {
    const wrapper = await mount({ found: digestBuild({ step: null, failure: digestFailure() }) })
    expect(wrapper.get(BUTTON).attributes('disabled')).toBeUndefined()
  })

  it('should look for the build again once it has started one', async () => {
    await click()
    expect(radioService.digestBuild).toHaveBeenCalledTimes(2)
  })

  it('should look for the build again when the rebuild is refused', async () => {
    await refuse(new ApiError(409, 'Conflict', 'Väljavõte on juba töös'))
    expect(radioService.digestBuild).toHaveBeenCalledTimes(2)
  })

  it('should say that a digest is already building', async () => {
    await refuse(new ApiError(409, 'Conflict', 'Väljavõte on juba töös'))
    expect(toast.error).toHaveBeenCalledWith('A digest is already building')
  })

  it.each([
    [
      'a server failure',
      new ApiError(500, 'Internal Server Error', 'Väljavõtte alustamine ebaõnnestus'),
    ],
    ['a lost connection', new Error('offline')],
  ])('should say the rebuild could not start after %s', async (_, error) => {
    await refuse(error)
    expect(toast.error).toHaveBeenCalledWith('Could not start the rebuild')
  })
})
