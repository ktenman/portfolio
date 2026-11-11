export const blurOnWheel = (event: WheelEvent) => {
  const target = event.target as HTMLInputElement
  if (target.type === 'number') target.blur()
}

const reloaded = 'portfolio_reloaded_for_new_build'

export const reloadOnce = () => {
  if (sessionStorage.getItem(reloaded)) return
  sessionStorage.setItem(reloaded, 'true')
  window.location.reload()
}

export const forgetReload = () => sessionStorage.removeItem(reloaded)

export const revealInRow = (row: Element | null, selector: string, align: 'nearest' | 'center') => {
  const item = row?.querySelector(selector)
  if (!row || !item) return
  const box = row.getBoundingClientRect()
  const { left, right } = item.getBoundingClientRect()
  row.scrollLeft +=
    align === 'center'
      ? (left + right - box.left - box.right) / 2
      : Math.min(0, left - box.left) + Math.max(0, right - box.right)
}
