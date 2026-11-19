import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioIcon from './radio-icon.vue'

const commands = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('path').map(path => path.attributes('d')!.replace(/[^A-Za-z]/g, ''))

describe('RadioIcon', () => {
  it('should draw play and pause with the same commands so one morphs into the other', async () => {
    const wrapper = mount(RadioIcon, { props: { name: 'play' } })
    const play = commands(wrapper)
    await wrapper.setProps({ name: 'pause' })
    expect([play.length, play]).toEqual([2, commands(wrapper)])
  })

  it('should keep the paths of the glyph when play turns to pause', async () => {
    const wrapper = mount(RadioIcon, { props: { name: 'play' } })
    const before = wrapper.findAll('path').map(path => path.element)
    await wrapper.setProps({ name: 'pause' })
    const after = wrapper.findAll('path')
    expect([after.map(path => path.element), after[0]!.attributes('d')]).toEqual([
      before,
      'M6 3L9 3C9.552 3 10 3.448 10 4L10 20C10 20.552 9.552 21 9 21L6 21C5.448 21 5 20.552 5 20L5 4C5 3.448 5.448 3 6 3Z',
    ])
  })
})
