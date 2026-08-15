export const blurOnWheel = (event: WheelEvent) => {
  const target = event.target as HTMLInputElement
  if (target.type === 'number') target.blur()
}
