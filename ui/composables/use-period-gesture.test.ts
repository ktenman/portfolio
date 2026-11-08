import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { ChartFrame } from '../services/chart-frame'
import type { ChartPeriod } from '../services/chart-period'
import { usePeriodGesture } from './use-period-gesture'

enableAutoUnmount(afterEach)

const FRAME: ChartFrame = {
  width: 400,
  left: 40,
  right: 360,
  top: 20,
  bottom: 220,
  axisY: 254.6,
  legendY: 276,
  legendLeft: 50,
  legendRight: 196,
  xs: [40, 360],
  dates: ['2026-01-01', '2026-01-21'],
}

const DAY_WIDTH = 16
const PLOT_Y = 100

const dayX = (day: number) => FRAME.left + DAY_WIDTH * day
const day = (offset: number) => `2026-01-${String(offset + 1).padStart(2, '0')}`
const days = (from: number, to: number): ChartPeriod => ({ from: day(from), to: day(to) })

interface HostOptions {
  period?: ChartPeriod | null
  enabled?: boolean
}

const mountGesture = async ({ period = null, enabled = true }: HostOptions = {}) => {
  const model = ref<ChartPeriod | null>(period)
  const zooms = ref(0)
  let gesture!: ReturnType<typeof usePeriodGesture>
  const wrapper = mount(
    defineComponent({
      setup() {
        const root = ref<HTMLElement | null>(null)
        gesture = usePeriodGesture({
          root,
          frame: ref(FRAME),
          period: model,
          enabled: ref(enabled),
          zoom: () => {
            zooms.value += 1
          },
        })
        return () =>
          h('div', { ref: root }, [h('span', { 'data-part': 'from' }), h('button', 'Clear')])
      },
    }),
    { attachTo: document.body }
  )
  await nextTick()
  return { root: wrapper.element as HTMLElement, period: model, zooms, gesture }
}

const pointer = (target: Element, type: string, x: number, init: PointerEventInit = {}) =>
  target.dispatchEvent(
    new PointerEvent(type, {
      bubbles: true,
      clientX: x,
      clientY: PLOT_Y,
      pointerId: 1,
      pointerType: 'mouse',
      button: 0,
      buttons: type === 'pointerup' ? 0 : 1,
      ...init,
    })
  )

const touch = (target: Element, type: string, x: number, init: PointerEventInit = {}) =>
  pointer(target, type, x, { pointerType: 'touch', ...init })

const mouseDrag = (root: Element, ...xs: number[]) => {
  const [first, ...rest] = xs
  pointer(root, 'pointerdown', first)
  rest.forEach(x => pointer(root, 'pointermove', x))
  pointer(root, 'pointerup', xs[xs.length - 1])
}

const pressEscape = () => window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

describe('usePeriodGesture with a mouse', () => {
  it('should draw a period across a drag', async () => {
    const { root, period } = await mountGesture()
    mouseDrag(root, dayX(2), dayX(5), dayX(10))

    expect(period.value).toEqual(days(2, 10))
  })

  it('should keep the period while the drag stays on the same day', async () => {
    const { root, period } = await mountGesture()
    pointer(root, 'pointerdown', dayX(2))
    pointer(root, 'pointermove', dayX(8))
    const drawn = period.value
    pointer(root, 'pointermove', dayX(8) + 1)

    expect(period.value).toBe(drawn)
  })

  it('should draw a period from right to left', async () => {
    const { root, period } = await mountGesture()
    mouseDrag(root, dayX(10), dayX(2))

    expect(period.value).toEqual(days(2, 10))
  })

  it('should mark the drag as a new period while it is drawn', async () => {
    const { root, gesture } = await mountGesture()
    pointer(root, 'pointerdown', dayX(2))
    pointer(root, 'pointermove', dayX(6))

    expect(gesture.drag.value).toBe('new')
  })

  it('should not start a drag before the pointer moves four pixels', async () => {
    const { root, gesture } = await mountGesture()
    pointer(root, 'pointerdown', dayX(2))
    pointer(root, 'pointermove', dayX(2) + 4)

    expect(gesture.drag.value).toBeNull()
  })

  it('should drop a drawn period shorter than two days', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    mouseDrag(root, dayX(2), dayX(3))

    expect(period.value).toBeNull()
  })

  it('should drop the period on a click outside it', async () => {
    const { root, period } = await mountGesture({ period: days(10, 15) })
    mouseDrag(root, dayX(2))

    expect(period.value).toBeNull()
  })

  it('should keep the period on a click inside it', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    mouseDrag(root, dayX(10))

    expect(period.value).toEqual(days(5, 15))
  })

  it('should move the start of the period when its grip is dragged', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    const grip = root.querySelector('[data-part="from"]') as Element
    pointer(grip, 'pointerdown', dayX(5))
    pointer(root, 'pointermove', dayX(2))
    pointer(root, 'pointerup', dayX(2))

    expect(period.value).toEqual(days(2, 15))
  })

  it('should slide the period through time when dragged from inside', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    mouseDrag(root, dayX(10), dayX(13))

    expect(period.value).toEqual(days(8, 18))
  })

  it('should ignore a press on a button', async () => {
    const { root, period } = await mountGesture({ period: days(10, 15) })
    mouseDrag(root.querySelector('button') as Element, dayX(2))

    expect(period.value).toEqual(days(10, 15))
  })

  it('should ignore a press below the plot', async () => {
    const { root, period } = await mountGesture({ period: days(10, 15) })
    pointer(root, 'pointerdown', dayX(2), { clientY: FRAME.legendY })
    pointer(root, 'pointerup', dayX(2), { clientY: FRAME.legendY })

    expect(period.value).toEqual(days(10, 15))
  })

  it('should ignore every press while selection is off', async () => {
    const { root, period } = await mountGesture({ enabled: false })
    mouseDrag(root, dayX(2), dayX(10))

    expect(period.value).toBeNull()
  })

  it('should bring back the period from before the press when the browser cancels the drag', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    pointer(root, 'pointerdown', dayX(1))
    pointer(root, 'pointermove', dayX(18))
    pointer(root, 'pointercancel', dayX(18))

    expect(period.value).toEqual(days(5, 15))
  })

  it('should bring back the period from before the press on Escape', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    pointer(root, 'pointerdown', dayX(1))
    pointer(root, 'pointermove', dayX(18))
    pressEscape()

    expect(period.value).toEqual(days(5, 15))
  })

  it('should drop the period on Escape', async () => {
    const { period } = await mountGesture({ period: days(5, 15) })
    pressEscape()

    expect(period.value).toBeNull()
  })

  it('should leave no period on Escape without one', async () => {
    const { period } = await mountGesture()
    pressEscape()

    expect(period.value).toBeNull()
  })

  it('should zoom on a double click inside the period', async () => {
    const { root, zooms } = await mountGesture({ period: days(5, 15) })
    root.dispatchEvent(
      new MouseEvent('dblclick', { bubbles: true, clientX: dayX(10), clientY: PLOT_Y })
    )

    expect(zooms.value).toBe(1)
  })

  it('should know the pointer is inside the period while it hovers there', async () => {
    const { root, gesture } = await mountGesture({ period: days(5, 15) })
    pointer(root, 'pointermove', dayX(10))

    expect(gesture.inside.value).toBe(true)
  })

  it('should keep the period when the mouse is released outside the chart', async () => {
    const { root, period } = await mountGesture()
    mouseDrag(root, dayX(2), dayX(40))

    expect(period.value).toEqual(days(2, 20))
  })

  it('should bring back the earlier period when the mouse moves on with no button held', async () => {
    const { root, period } = await mountGesture({ period: days(14, 18) })
    pointer(root, 'pointerdown', dayX(2))
    pointer(root, 'pointermove', dayX(6))
    pointer(root, 'pointermove', dayX(12), { buttons: 0 })

    expect(period.value).toEqual(days(14, 18))
  })

  it('should bring back the earlier period when the chart loses the pointer mid-drag', async () => {
    const { root, period } = await mountGesture({ period: days(14, 18) })
    pointer(root, 'pointerdown', dayX(2))
    pointer(root, 'pointermove', dayX(6))
    pointer(root, 'lostpointercapture', dayX(6))

    expect(period.value).toEqual(days(14, 18))
  })

  it('should treat a press after a release that never arrived as a fresh click', async () => {
    const { root, period } = await mountGesture()
    pointer(root, 'pointerdown', dayX(2))
    pointer(root, 'pointermove', dayX(6))
    mouseDrag(root, dayX(12))

    expect(period.value).toBeNull()
  })

  it('should show a grab cursor inside the period', async () => {
    const { root, gesture } = await mountGesture({ period: days(5, 15) })
    pointer(root, 'pointermove', dayX(10))

    expect(gesture.cursor.value).toBe('grab')
  })

  it('should show a grabbing cursor while the period slides', async () => {
    const { root, gesture } = await mountGesture({ period: days(5, 15) })
    pointer(root, 'pointerdown', dayX(10))
    pointer(root, 'pointermove', dayX(13))

    expect(gesture.cursor.value).toBe('grabbing')
  })

  it('should show a crosshair over the plot outside the period', async () => {
    const { root, gesture } = await mountGesture({ period: days(5, 15) })
    pointer(root, 'pointermove', dayX(2))

    expect(gesture.cursor.value).toBe('crosshair')
  })
})

describe('usePeriodGesture with a finger', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should show the hold ring where the finger presses', async () => {
    const { root, gesture } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))

    expect(gesture.ring.value).toEqual({ x: dayX(5), y: PLOT_Y })
  })

  it('should start a period on the pressed day after a 350 ms hold', async () => {
    const { root, period } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    vi.advanceTimersByTime(350)

    expect(period.value).toEqual(days(5, 5))
  })

  it('should not start a period before the hold completes', async () => {
    const { root, period } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    vi.advanceTimersByTime(349)

    expect(period.value).toBeNull()
  })

  it('should draw the period as the held finger slides', async () => {
    const { root, period } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    vi.advanceTimersByTime(350)
    touch(root, 'pointermove', dayX(9))
    touch(root, 'pointerup', dayX(9))

    expect(period.value).toEqual(days(5, 9))
  })

  it('should cancel the hold when the finger moves more than 8 px first', async () => {
    const { root, period } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    touch(root, 'pointermove', dayX(5) + 9)
    vi.advanceTimersByTime(350)

    expect(period.value).toBeNull()
  })

  it('should keep the previous period when a held finger draws less than two days', async () => {
    const { root, period } = await mountGesture({ period: days(5, 15) })
    touch(root, 'pointerdown', dayX(2))
    vi.advanceTimersByTime(350)
    touch(root, 'pointermove', dayX(3))
    touch(root, 'pointerup', dayX(3))

    expect(period.value).toEqual(days(5, 15))
  })

  it('should not keep a one-day period when a second finger taps during the hold', async () => {
    const { root, period } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    vi.advanceTimersByTime(350)
    touch(root, 'pointerdown', dayX(9), { pointerId: 2 })
    touch(root, 'pointerup', dayX(9), { pointerId: 2 })
    touch(root, 'pointerup', dayX(5))

    expect(period.value).toBeNull()
  })

  it('should keep the period on a tap outside it', async () => {
    const { root, period } = await mountGesture({ period: days(10, 15) })
    touch(root, 'pointerdown', dayX(2))
    touch(root, 'pointerup', dayX(2))

    expect(period.value).toEqual(days(10, 15))
  })

  it('should stop the page scrolling under a held finger', async () => {
    const { root } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    vi.advanceTimersByTime(350)
    const scrolled = root.dispatchEvent(
      new TouchEvent('touchmove', { bubbles: true, cancelable: true })
    )

    expect(scrolled).toBe(false)
  })

  it('should let the page scroll under a plain swipe', async () => {
    const { root } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    const scrolled = root.dispatchEvent(
      new TouchEvent('touchmove', { bubbles: true, cancelable: true })
    )

    expect(scrolled).toBe(true)
  })

  it('should block the long-press menu while a finger holds the chart', async () => {
    const { root } = await mountGesture()
    touch(root, 'pointerdown', dayX(5))
    const opened = root.dispatchEvent(new MouseEvent('contextmenu', { cancelable: true }))

    expect(opened).toBe(false)
  })
})
