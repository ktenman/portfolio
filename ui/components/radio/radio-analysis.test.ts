import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioAnalysis from './radio-analysis.vue'
import { recording } from './radio-fixture'

describe('RadioAnalysis', () => {
  beforeEach(() => localStorage.clear())

  const render = () => mount(RadioAnalysis, { props: { recording: recording() } })

  it('should show the reads beside the title', () => {
    expect(render().get('h3').element.parentElement!.textContent).toContain('3 / 3')
  })

  it('should start folded', () => {
    expect(render().get('button').attributes('aria-expanded')).toBe('false')
  })
})
