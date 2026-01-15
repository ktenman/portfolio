import { config } from '@vue/test-utils'
import { vi } from 'vitest'

vi.mock('@guolao/vue-monaco-editor', () => ({
  VueMonacoEditor: {
    name: 'VueMonacoEditor',
    props: ['value', 'language', 'options', 'theme'],
    template: '<div class="mock-monaco-editor"></div>',
  },
  loader: {
    __getMonacoInstance: vi.fn(),
    config: vi.fn(),
    init: vi.fn().mockResolvedValue({}),
  },
}))

config.global.stubs = {
  teleport: true,
}

window.matchMedia = vi.fn().mockImplementation(query => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}))

Object.defineProperty(window, 'location', {
  value: {
    href: 'http://localhost:3000',
    pathname: '/',
    search: '',
    hash: '',
    reload: vi.fn(),
  },
  writable: true,
})
