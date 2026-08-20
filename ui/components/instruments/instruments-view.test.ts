import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { flushPromises, VueWrapper } from '@vue/test-utils'
import { instrumentsService } from '../../services/api'
import { createInstrumentDto } from '../../tests/fixtures'
import { mountAndSettle, setupInstrumentsViewMocks } from './instruments-view-fixture'

const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()

vi.mock('../../services/api')
vi.mock('../../composables/use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))
vi.mock('../../composables/use-toast', () => ({
  useToast: () => ({
    success: mockToastSuccess,
    error: mockToastError,
  }),
}))
vi.mock('@vueuse/core', () => ({
  useLocalStorage: vi.fn((_key: string, defaultValue: any) => ref(defaultValue)),
  onClickOutside: vi.fn(),
}))

const requestAdd = async (wrapper: VueWrapper) => {
  wrapper.findComponent({ name: 'CrudLayout' }).vm.$emit('add')
  await flushPromises()
}

const stubCreate = () =>
  vi
    .mocked(instrumentsService.create)
    .mockResolvedValue(createInstrumentDto({ id: 3, symbol: 'ÕIE', name: 'Õie Investeeringud AS' }))

const mountAddAndSave = async () => {
  const mounted = await mountAndSettle()
  await requestAdd(mounted.wrapper)
  await mounted.wrapper.find('#stub-save-button').trigger('click')
  await flushPromises()
  return mounted
}

describe('InstrumentsView', () => {
  setupInstrumentsViewMocks()

  describe('query operations', () => {
    it('should fetch instruments on mount', async () => {
      await mountAndSettle()

      expect(instrumentsService.getAll).toHaveBeenCalled()
    })
  })

  describe('modal operations', () => {
    it('should not offer an add button in the header', async () => {
      const { wrapper } = await mountAndSettle()

      expect(wrapper.find('#stub-add-button').exists()).toBe(false)
    })

    it('should clear selectedItem and show modal when opening add modal', async () => {
      const { wrapper } = await mountAndSettle()

      await requestAdd(wrapper)

      const modal = wrapper.find('#stub-modal')
      const instrumentData = JSON.parse(modal.attributes('data-instrument') || '{}')
      expect(instrumentData).toEqual({})
      expect(modal.attributes('data-open')).toBe('true')
    })
  })

  describe('create mutation', () => {
    it('should create instrument when selectedItem has no id', async () => {
      stubCreate()

      const { wrapper, queryClient } = await mountAddAndSave()

      expect(instrumentsService.create).toHaveBeenCalled()
      expect(instrumentsService.update).not.toHaveBeenCalled()
      expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['instruments'] })
      expect(wrapper.find('#stub-modal').attributes('data-open')).toBe('false')
    })

    it('should not confirm a successful save in the UI', async () => {
      stubCreate()

      await mountAddAndSave()

      expect(mockToastSuccess).not.toHaveBeenCalled()
    })

    it('should handle create error and show error message', async () => {
      const error = new Error('Network error')
      vi.mocked(instrumentsService.create).mockRejectedValue(error)

      const { wrapper } = await mountAddAndSave()

      expect(mockToastError).toHaveBeenCalledWith(`Failed to save instrument: ${error.message}`)
      expect(wrapper.find('#stub-modal').attributes('data-open')).toBe('true')
    })
  })

  describe('mutation state management', () => {
    it('should clear selectedItem after successful create', async () => {
      stubCreate()

      const { wrapper } = await mountAddAndSave()

      const modalAfterSave = wrapper.find('#stub-modal')
      expect(JSON.parse(modalAfterSave.attributes('data-instrument') || '{}')).toEqual({})
    })
  })
})
