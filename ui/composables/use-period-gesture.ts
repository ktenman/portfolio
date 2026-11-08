import { computed, ref, type Ref } from 'vue'
import { onKeyStroke, useEventListener, useTimeoutFn } from '@vueuse/core'
import { dateAtX, frameBounds, xOfDate, type ChartFrame } from '../services/chart-frame'
import {
  MIN_PERIOD_DAYS,
  moveEdge,
  periodDays,
  slidePeriod,
  spanPeriod,
  type ChartPeriod,
  type PeriodEdge,
} from '../services/chart-period'
import { daysBetween } from '../utils/iso-dates'

export type PeriodDrag = 'new' | 'win' | PeriodEdge

interface Point {
  x: number
  y: number
}

interface Press extends Point {
  id: number
  touch: boolean
  kind: PeriodDrag
  before: ChartPeriod | null
}

interface GestureOptions {
  root: Ref<HTMLElement | null>
  frame: Ref<ChartFrame | null>
  period: Ref<ChartPeriod | null>
  enabled: Ref<boolean>
  zoom: () => void
}

const HOLD_MS = 350
const HOLD_SLOP = 8
const DRAG_SLOP = 4

const CURSORS: Record<PeriodDrag, string> = {
  new: 'crosshair',
  win: 'grabbing',
  from: 'ew-resize',
  to: 'ew-resize',
}

const onPlot = (frame: ChartFrame, { x, y }: Point): boolean =>
  x >= frame.left && x <= frame.right && y >= frame.top && y <= frame.bottom

const partOf = (event: Event): PeriodEdge | null => {
  const part = (event.target as Element).closest('[data-part]')?.getAttribute('data-part')
  return part === 'from' || part === 'to' ? part : null
}

export function usePeriodGesture({ root, frame, period, enabled, zoom }: GestureOptions) {
  const drag = ref<PeriodDrag | null>(null)
  const ring = ref<Point | null>(null)
  const inside = ref(false)
  const hovering = ref(false)
  let press: Press | null = null

  const hold = useTimeoutFn(
    (point: Point) => {
      const day = dateAtX(frame.value as ChartFrame, point.x)
      ring.value = null
      period.value = { from: day, to: day }
      drag.value = 'new'
    },
    HOLD_MS,
    { immediate: false }
  )

  const pointOf = (event: MouseEvent): Point => {
    const box = (root.value as HTMLElement).getBoundingClientRect()
    return { x: event.clientX - box.left, y: event.clientY - box.top }
  }

  const isInside = (view: ChartFrame, point: Point): boolean => {
    const current = period.value
    if (!current || !onPlot(view, point)) return false
    return point.x > xOfDate(view, current.from) && point.x < xOfDate(view, current.to)
  }

  const kindOf = (event: PointerEvent, view: ChartFrame, point: Point): PeriodDrag | null => {
    const part = partOf(event)
    if (part) return part
    if (!onPlot(view, point)) return null
    return event.pointerType === 'mouse' && isInside(view, point) ? 'win' : 'new'
  }

  const finish = () => {
    hold.stop()
    press = null
    ring.value = null
    drag.value = null
  }

  const restore = (before: ChartPeriod | null) => {
    finish()
    period.value = before
  }

  const arm = (point: Point) => {
    ring.value = point
    hold.start(point)
  }

  const place = (next: ChartPeriod) => {
    const current = period.value
    if (next.from !== current?.from || next.to !== current?.to) period.value = next
  }

  const follow = (view: ChartFrame, active: Press, point: Point) => {
    const date = dateAtX(view, point.x)
    const kind = drag.value
    if (kind === 'new') place(spanPeriod(dateAtX(view, active.x), date))
    if (kind === 'win' && active.before) {
      const shift = daysBetween(dateAtX(view, active.x), date)
      place(slidePeriod(active.before, shift, frameBounds(view)))
    }
    if ((kind === 'from' || kind === 'to') && period.value) {
      place(moveEdge(period.value, kind, date, frameBounds(view)))
    }
  }

  const hover = (event: PointerEvent, view: ChartFrame, point: Point) => {
    hovering.value = onPlot(view, point)
    inside.value = event.pointerType === 'mouse' && isInside(view, point)
  }

  const onDown = (event: PointerEvent) => {
    const view = frame.value
    if (!enabled.value || !view || event.button !== 0) return
    if (press && event.pointerId !== press.id) return
    if (press) restore(press.before)
    if ((event.target as Element).closest('button')) return
    const point = pointOf(event)
    const kind = kindOf(event, view, point)
    if (!kind) return
    const touch = event.pointerType !== 'mouse'
    press = { ...point, id: event.pointerId, touch, kind, before: period.value }
    if (kind === 'from' || kind === 'to') drag.value = kind
    else if (touch) arm(point)
    if (!touch) root.value?.setPointerCapture(event.pointerId)
  }

  const onMove = (event: PointerEvent) => {
    const view = frame.value
    if (!view) return
    const point = pointOf(event)
    if (!press) return hover(event, view, point)
    if (event.pointerId !== press.id) return
    if (!press.touch && event.buttons === 0) return restore(press.before)
    if (drag.value) return follow(view, press, point)
    const distance = Math.hypot(point.x - press.x, point.y - press.y)
    if (press.touch && distance > HOLD_SLOP) return finish()
    if (press.touch || distance <= DRAG_SLOP) return
    drag.value = press.kind
    follow(view, press, point)
  }

  const onUp = (event: PointerEvent) => {
    if (!press || event.pointerId !== press.id) return
    const { kind, touch, before } = press
    const drew = drag.value !== null
    const short = period.value !== null && periodDays(period.value) < MIN_PERIOD_DAYS
    finish()
    if (kind !== 'new') return
    if (!drew && !touch) period.value = null
    if (drew && short) period.value = touch ? before : null
  }

  const onCancel = (event: PointerEvent) => {
    if (press && event.pointerId === press.id) restore(press.before)
  }

  const onDoubleClick = (event: MouseEvent) => {
    const view = frame.value
    if (enabled.value && view && isInside(view, pointOf(event))) zoom()
  }

  const onLeave = () => {
    hovering.value = false
    inside.value = false
  }

  useEventListener(root, 'pointerdown', onDown)
  useEventListener(root, 'pointermove', onMove)
  useEventListener(root, 'pointerup', onUp)
  useEventListener(root, 'pointercancel', onCancel)
  useEventListener(root, 'lostpointercapture', onCancel)
  useEventListener(root, 'pointerleave', onLeave)
  useEventListener(root, 'dblclick', onDoubleClick)
  useEventListener(root, 'contextmenu', (event: Event) => {
    if (press?.touch) event.preventDefault()
  })
  useEventListener(
    root,
    'touchmove',
    (event: TouchEvent) => {
      if (drag.value) event.preventDefault()
    },
    { passive: false }
  )
  onKeyStroke('Escape', () => {
    if (press) return restore(press.before)
    if (period.value) period.value = null
  })

  const cursor = computed(() => {
    if (!enabled.value) return undefined
    if (drag.value) return CURSORS[drag.value]
    if (inside.value) return 'grab'
    return hovering.value ? 'crosshair' : undefined
  })

  return { drag, ring, inside, cursor }
}
