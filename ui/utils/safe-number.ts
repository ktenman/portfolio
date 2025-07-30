export function SafeNumber(value: string | number | null | undefined): number | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  const num = Number(value)
  return isNaN(num) ? undefined : num
}
