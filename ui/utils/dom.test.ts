import { beforeEach, describe, expect, it, vi } from 'vitest'
import { forgetReload, reloadOnce, revealInRow } from './dom'

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

describe('revealInRow', () => {
  const rect = (left: number, right: number) => ({ left, right }) as DOMRect

  const row = (scrollLeft: number, left: number, right: number) => {
    const element = document.createElement('div')
    const item = document.createElement('span')
    item.className = 'item'
    item.getBoundingClientRect = () => rect(left, right)
    element.append(item)
    element.scrollLeft = scrollLeft
    element.getBoundingClientRect = () => rect(0, 300)
    return element
  }

  it('scrolls the row forward until an item past its right edge shows', () => {
    const strip = row(0, 320, 380)
    revealInRow(strip, '.item', 'nearest')
    expect(strip.scrollLeft).toBe(80)
  })

  it('scrolls the row back until an item past its left edge shows', () => {
    const strip = row(200, -50, 10)
    revealInRow(strip, '.item', 'nearest')
    expect(strip.scrollLeft).toBe(150)
  })

  it('leaves the row alone when the item already shows', () => {
    const strip = row(40, 100, 160)
    revealInRow(strip, '.item', 'nearest')
    expect(strip.scrollLeft).toBe(40)
  })

  it('leaves the row alone when no item matches', () => {
    const strip = row(40, 320, 380)
    revealInRow(strip, '.missing', 'nearest')
    expect(strip.scrollLeft).toBe(40)
  })

  it('centres an item in the row', () => {
    const strip = row(0, 400, 460)
    revealInRow(strip, '.item', 'center')
    expect(strip.scrollLeft).toBe(280)
  })
})
