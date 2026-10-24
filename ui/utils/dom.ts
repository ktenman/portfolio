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
