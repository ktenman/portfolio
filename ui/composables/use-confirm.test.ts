import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { provideConfirm, useConfirm } from './use-confirm'

describe('useConfirm', () => {
  const createWrapper = () => {
    let confirmState: any
    let confirmFn: any

    const ChildComponent = defineComponent({
      name: 'ChildComponent',
      setup() {
        const { confirm } = useConfirm()
        confirmFn = confirm
        return { confirm }
      },
      template: '<div>Child</div>',
    })

    const wrapper = mount(
      defineComponent({
        name: 'ParentComponent',
        components: { ChildComponent },
        setup() {
          confirmState = provideConfirm()
          return { state: confirmState }
        },
        template: '<div><ChildComponent /></div>',
      })
    )

    return { wrapper, confirmState, confirmFn }
  }

  describe('provideConfirm', () => {
    it('should initialize with default state', () => {
      const { confirmState } = createWrapper()

      expect(confirmState.isOpen.value).toBe(false)
      expect(confirmState.options.value).toEqual({})
    })

    it('should open dialog with default options', async () => {
      const { confirmState, confirmFn } = createWrapper()

      confirmFn()
      await nextTick()

      expect(confirmState.isOpen.value).toBe(true)
      expect(confirmState.options.value).toEqual({
        title: 'Confirm',
        message: 'Are you sure?',
        confirmText: 'Confirm',
        cancelText: 'Cancel',
        confirmClass: 'btn-primary',
      })
    })

    it('should merge custom options with defaults', async () => {
      const { confirmState, confirmFn } = createWrapper()

      const customOptions = {
        title: 'Delete Item',
        message: 'This action cannot be undone.',
        confirmClass: 'btn-danger',
      }

      confirmFn(customOptions)
      await nextTick()

      expect(confirmState.options.value).toEqual({
        title: 'Delete Item',
        message: 'This action cannot be undone.',
        confirmText: 'Confirm',
        cancelText: 'Cancel',
        confirmClass: 'btn-danger',
      })
    })

    it('should resolve promise with true when confirmed', async () => {
      const { confirmState, confirmFn } = createWrapper()

      const confirmPromise = confirmFn()
      await nextTick()

      confirmState.handleConfirm()

      const result = await confirmPromise
      expect(result).toBe(true)
      expect(confirmState.isOpen.value).toBe(false)
    })

    it('should resolve promise with false when cancelled', async () => {
      const { confirmState, confirmFn } = createWrapper()

      const confirmPromise = confirmFn()
      await nextTick()

      confirmState.handleCancel()

      const result = await confirmPromise
      expect(result).toBe(false)
      expect(confirmState.isOpen.value).toBe(false)
    })

    it('should handle multiple confirm calls sequentially', async () => {
      const { confirmState, confirmFn } = createWrapper()

      const firstPromise = confirmFn({ title: 'First' })
      await nextTick()
      expect(confirmState.options.value.title).toBe('First')

      confirmState.handleConfirm()
      const firstResult = await firstPromise
      expect(firstResult).toBe(true)

      const secondPromise = confirmFn({ title: 'Second' })
      await nextTick()
      expect(confirmState.options.value.title).toBe('Second')

      confirmState.handleCancel()
      const secondResult = await secondPromise
      expect(secondResult).toBe(false)
    })

    it('should reject first promise when confirm called again before resolution', async () => {
      const { confirmState, confirmFn } = createWrapper()

      const firstPromise = confirmFn({ title: 'First' })
      await nextTick()
      expect(confirmState.options.value.title).toBe('First')

      const secondPromise = confirmFn({ title: 'Second' })
      await nextTick()

      expect(confirmState.options.value.title).toBe('Second')

      const firstResult = await firstPromise
      expect(firstResult).toBe(false)

      confirmState.handleConfirm()
      const secondResult = await secondPromise
      expect(secondResult).toBe(true)
    })
  })

  describe('useConfirm without provider', () => {
    it('should throw error when used without provider', () => {
      mount(
        defineComponent({
          setup() {
            expect(() => useConfirm()).toThrow(
              'useConfirm must be used within a component tree that has called provideConfirm'
            )
          },
          template: '<div>Test</div>',
        })
      )
    })
  })

  describe('integration test', () => {
    it('should work correctly with user interaction', async () => {
      let confirmResult: boolean | null = null

      const wrapper = mount(
        defineComponent({
          components: {
            ChildComponent: defineComponent({
              setup() {
                const { confirm } = useConfirm()

                const handleAction = async () => {
                  confirmResult = await confirm({
                    title: 'Delete Record',
                    message: 'Are you sure you want to delete this record?',
                    confirmText: 'Delete',
                    confirmClass: 'btn-danger',
                  })
                }

                return { handleAction }
              },
              template: '<button @click="handleAction">Delete</button>',
            }),
          },
          setup() {
            const state = provideConfirm()
            return { state }
          },
          template: '<div><ChildComponent /></div>',
        })
      )

      await wrapper.find('button').trigger('click')
      await nextTick()

      const state = wrapper.vm.state
      expect(state.isOpen.value).toBe(true)
      expect(state.options.value.title).toBe('Delete Record')

      state.handleConfirm()
      await nextTick()

      expect(confirmResult).toBe(true)
      expect(state.isOpen.value).toBe(false)
    })
  })
})
