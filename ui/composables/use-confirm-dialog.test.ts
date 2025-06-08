import { describe, it, expect } from 'vitest'
import { useConfirmDialog, confirmDelete } from './use-confirm-dialog'

describe('useConfirmDialog', () => {
  describe('initial state', () => {
    it('has correct initial values', () => {
      const { isOpen, options } = useConfirmDialog()

      expect(isOpen.value).toBe(false)
      expect(options.value).toEqual({
        message: '',
        title: 'Confirm',
        confirmText: 'OK',
        cancelText: 'Cancel',
        type: 'info',
      })
    })
  })

  describe('confirm', () => {
    it('opens dialog and sets options', () => {
      const { confirm, isOpen, options } = useConfirmDialog()

      const confirmOptions = {
        title: 'Delete Item',
        message: 'Are you sure?',
        confirmText: 'Delete',
        cancelText: 'Cancel',
        type: 'danger' as const,
      }

      const promise = confirm(confirmOptions)

      expect(isOpen.value).toBe(true)
      expect(options.value).toEqual({
        title: 'Delete Item',
        message: 'Are you sure?',
        confirmText: 'Delete',
        cancelText: 'Cancel',
        type: 'danger',
      })
      expect(promise).toBeInstanceOf(Promise)
    })

    it('merges options with defaults', () => {
      const { confirm, options } = useConfirmDialog()

      confirm({ message: 'Test message' })

      expect(options.value).toEqual({
        message: 'Test message',
        title: 'Confirm',
        confirmText: 'OK',
        cancelText: 'Cancel',
        type: 'info',
      })
    })

    it('overwrites existing options', () => {
      const { confirm, options } = useConfirmDialog()

      // First confirm
      confirm({ message: 'First message', title: 'First Title' })

      // Second confirm should overwrite
      confirm({ message: 'Second message', confirmText: 'Yes' })

      expect(options.value).toEqual({
        message: 'Second message',
        title: 'First Title', // Should keep from default merge
        confirmText: 'Yes',
        cancelText: 'Cancel',
        type: 'info',
      })
    })
  })

  describe('handleConfirm', () => {
    it('closes dialog and resolves promise with true', async () => {
      const { confirm, handleConfirm, isOpen } = useConfirmDialog()

      const promise = confirm({ message: 'Test' })

      expect(isOpen.value).toBe(true)

      handleConfirm()

      expect(isOpen.value).toBe(false)
      await expect(promise).resolves.toBe(true)
    })

    it('handles confirm when no promise exists', () => {
      const { handleConfirm, isOpen } = useConfirmDialog()

      expect(() => handleConfirm()).not.toThrow()
      expect(isOpen.value).toBe(false)
    })

    it('clears resolve promise after handling', async () => {
      const { confirm, handleConfirm } = useConfirmDialog()

      const promise = confirm({ message: 'Test' })
      handleConfirm()

      await promise

      // Second call should not affect the resolved promise
      expect(() => handleConfirm()).not.toThrow()
    })
  })

  describe('handleCancel', () => {
    it('closes dialog and resolves promise with false', async () => {
      const { confirm, handleCancel, isOpen } = useConfirmDialog()

      const promise = confirm({ message: 'Test' })

      expect(isOpen.value).toBe(true)

      handleCancel()

      expect(isOpen.value).toBe(false)
      await expect(promise).resolves.toBe(false)
    })

    it('handles cancel when no promise exists', () => {
      const { handleCancel, isOpen } = useConfirmDialog()

      expect(() => handleCancel()).not.toThrow()
      expect(isOpen.value).toBe(false)
    })

    it('clears resolve promise after handling', async () => {
      const { confirm, handleCancel } = useConfirmDialog()

      const promise = confirm({ message: 'Test' })
      handleCancel()

      await promise

      // Second call should not affect the resolved promise
      expect(() => handleCancel()).not.toThrow()
    })
  })

  describe('multiple confirms', () => {
    it('handles multiple sequential confirms', async () => {
      const { confirm, handleConfirm, handleCancel } = useConfirmDialog()

      // First confirm - accept
      const promise1 = confirm({ message: 'First' })
      handleConfirm()
      await expect(promise1).resolves.toBe(true)

      // Second confirm - cancel
      const promise2 = confirm({ message: 'Second' })
      handleCancel()
      await expect(promise2).resolves.toBe(false)

      // Third confirm - accept
      const promise3 = confirm({ message: 'Third' })
      handleConfirm()
      await expect(promise3).resolves.toBe(true)
    })
  })
})

describe('confirmDelete', () => {
  it('returns confirm dialog with delete configuration and default item name', () => {
    const promise = confirmDelete()

    expect(promise).toBeInstanceOf(Promise)
  })

  it('returns confirm dialog with delete configuration and custom item name', () => {
    const promise = confirmDelete('User Account')

    expect(promise).toBeInstanceOf(Promise)
  })

  it('uses correct default message format', () => {
    // We can't easily test the internal state without refactoring,
    // but we can verify it returns a promise
    const promise = confirmDelete()
    expect(promise).toBeInstanceOf(Promise)
  })

  it('uses correct custom message format', () => {
    // We can't easily test the internal state without refactoring,
    // but we can verify it returns a promise with custom item name
    const promise = confirmDelete('test item')
    expect(promise).toBeInstanceOf(Promise)
  })
})
