import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { h } from 'vue'
import ConfigDialog from './config-dialog.vue'

vi.mock('bootstrap', () => {
  return {
    Modal: class {
      show = vi.fn()
      hide = vi.fn()
      dispose = vi.fn()
    },
  }
})

vi.mock('@guolao/vue-monaco-editor', () => ({
  VueMonacoEditor: {
    name: 'VueMonacoEditor',
    props: ['value', 'language', 'options', 'theme'],
    setup(props: { value: string }) {
      return () => h('div', { class: 'mock-monaco-editor' }, props.value)
    },
  },
}))

describe('ConfigDialog', () => {
  const defaultConfig = {
    allocations: [
      { instrumentId: 1, value: 60 },
      { instrumentId: 2, value: 40 },
    ],
    inputMode: 'percentage' as const,
  }

  const validEtfIds = new Set([1, 2, 3])

  const defaultProps = {
    modelValue: false,
    mode: 'export' as const,
    config: defaultConfig,
    validEtfIds,
    modalId: 'testModal',
  }

  let wrapper: VueWrapper

  beforeEach(() => {
    const modalElement = document.createElement('div')
    modalElement.id = 'testModal'
    document.body.appendChild(modalElement)
  })

  afterEach(() => {
    wrapper?.unmount()
    const modalElement = document.getElementById('testModal')
    if (modalElement) {
      document.body.removeChild(modalElement)
    }
  })

  describe('export mode', () => {
    it('should render export title', () => {
      wrapper = mount(ConfigDialog, { props: defaultProps })
      expect(wrapper.text()).toContain('Export Configuration')
    })

    it('should show config preview in export mode', () => {
      wrapper = mount(ConfigDialog, { props: defaultProps })
      expect(wrapper.find('.editor-container').exists()).toBe(true)
      expect(wrapper.find('.mock-monaco-editor').text()).toContain('instrumentId')
    })

    it('should render Download button in export mode', () => {
      wrapper = mount(ConfigDialog, { props: defaultProps })
      expect(wrapper.text()).toContain('Download')
    })

    it('should emit export and update:modelValue when download is clicked', async () => {
      const createObjectURL = vi.fn(() => 'blob:test')
      const revokeObjectURL = vi.fn()
      global.URL.createObjectURL = createObjectURL
      global.URL.revokeObjectURL = revokeObjectURL

      wrapper = mount(ConfigDialog, { props: { ...defaultProps, modelValue: true } })
      const downloadBtn = wrapper.findAll('button').find(b => b.text() === 'Download')
      await downloadBtn?.trigger('click')

      expect(wrapper.emitted('export')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
    })
  })

  describe('import mode', () => {
    const importProps = { ...defaultProps, mode: 'import' as const }

    it('should render import title', () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      expect(wrapper.text()).toContain('Import Configuration')
    })

    it('should show file drop zone initially', () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      expect(wrapper.find('.file-drop-zone').exists()).toBe(true)
      expect(wrapper.text()).toContain('Click to select or drag a JSON file here')
    })

    it('should have hidden file input', () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const fileInput = wrapper.find('input[type="file"]')
      expect(fileInput.exists()).toBe(true)
      expect(fileInput.attributes('accept')).toBe('.json')
    })

    it('should render Import button disabled initially', () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const importBtn = wrapper.findAll('button').find(b => b.text() === 'Import')
      expect(importBtn?.attributes('disabled')).toBeDefined()
    })

    it('should process valid JSON file', async () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const fileInput = wrapper.find('input[type="file"]')

      const testData = {
        allocations: [{ instrumentId: 1, value: 100 }],
        inputMode: 'percentage',
      }
      const file = new File([JSON.stringify(testData)], 'test.json', { type: 'application/json' })
      Object.defineProperty(fileInput.element, 'files', { value: [file] })
      await fileInput.trigger('change')
      await new Promise(resolve => setTimeout(resolve, 50))

      expect(wrapper.find('.import-preview').exists()).toBe(true)
      expect(wrapper.text()).toContain('Preview of configuration to import')
    })

    it('should show error for invalid JSON', async () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const fileInput = wrapper.find('input[type="file"]')

      const file = new File(['invalid json'], 'test.json', { type: 'application/json' })
      Object.defineProperty(fileInput.element, 'files', { value: [file] })
      await fileInput.trigger('change')
      await new Promise(resolve => setTimeout(resolve, 50))

      expect(wrapper.text()).toContain('Invalid JSON file')
    })

    it('should show error when JSON is missing required fields', async () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const fileInput = wrapper.find('input[type="file"]')

      const file = new File(['{"someField": "value"}'], 'test.json', { type: 'application/json' })
      Object.defineProperty(fileInput.element, 'files', { value: [file] })
      await fileInput.trigger('change')
      await new Promise(resolve => setTimeout(resolve, 50))

      expect(wrapper.text()).toContain('Invalid configuration file format')
    })

    it('should show warning when some ETFs are not available', async () => {
      const limitedValidIds = new Set([1])
      wrapper = mount(ConfigDialog, {
        props: { ...importProps, validEtfIds: limitedValidIds },
      })
      const fileInput = wrapper.find('input[type="file"]')

      const testData = {
        allocations: [
          { instrumentId: 1, value: 50 },
          { instrumentId: 999, value: 50 },
        ],
        inputMode: 'percentage',
      }
      const file = new File([JSON.stringify(testData)], 'test.json', { type: 'application/json' })
      Object.defineProperty(fileInput.element, 'files', { value: [file] })
      await fileInput.trigger('change')
      await new Promise(resolve => setTimeout(resolve, 50))

      expect(wrapper.text()).toContain('1 ETF(s) were removed because they are not available')
    })

    it('should emit import event with data when Import is clicked', async () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const fileInput = wrapper.find('input[type="file"]')

      const testData = {
        allocations: [{ instrumentId: 1, value: 100 }],
        inputMode: 'percentage',
      }
      const file = new File([JSON.stringify(testData)], 'test.json', { type: 'application/json' })
      Object.defineProperty(fileInput.element, 'files', { value: [file] })
      await fileInput.trigger('change')
      await new Promise(resolve => setTimeout(resolve, 50))

      const importBtn = wrapper.findAll('button').find(b => b.text() === 'Import')
      await importBtn?.trigger('click')

      expect(wrapper.emitted('import')).toBeTruthy()
      expect(wrapper.emitted('import')?.[0][0]).toEqual(testData)
    })

    it('should reset import state when Choose Different File is clicked', async () => {
      wrapper = mount(ConfigDialog, { props: importProps })
      const fileInput = wrapper.find('input[type="file"]')

      const testData = {
        allocations: [{ instrumentId: 1, value: 100 }],
        inputMode: 'percentage',
      }
      const file = new File([JSON.stringify(testData)], 'test.json', { type: 'application/json' })
      Object.defineProperty(fileInput.element, 'files', { value: [file] })
      await fileInput.trigger('change')
      await new Promise(resolve => setTimeout(resolve, 50))

      expect(wrapper.find('.import-preview').exists()).toBe(true)

      const resetBtn = wrapper.findAll('button').find(b => b.text() === 'Choose Different File')
      await resetBtn?.trigger('click')

      expect(wrapper.find('.file-drop-zone').exists()).toBe(true)
      expect(wrapper.find('.import-preview').exists()).toBe(false)
    })
  })

  describe('common functionality', () => {
    it('should emit update:modelValue false when Cancel is clicked', async () => {
      wrapper = mount(ConfigDialog, { props: defaultProps })
      const cancelBtn = wrapper.findAll('button').find(b => b.text() === 'Cancel')
      await cancelBtn?.trigger('click')

      expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
    })

    it('should emit update:modelValue false when close button is clicked', async () => {
      wrapper = mount(ConfigDialog, { props: defaultProps })
      const closeBtn = wrapper.find('.btn-close')
      await closeBtn.trigger('click')

      expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
    })
  })
})
