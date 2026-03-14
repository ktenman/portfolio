import { type ComputedRef, type Ref } from 'vue'

export function useInstrumentColors(
  selectedIds: Ref<number[]>,
  instrumentColorMap: ComputedRef<Map<number, string>>,
  colors: readonly string[]
) {
  const tagColorFor = (id: number): string => {
    const fromChart = instrumentColorMap.value.get(id)
    if (fromChart) return fromChart
    const idx = selectedIds.value.indexOf(id)
    return colors[Math.max(0, idx) % colors.length]
  }

  const isLightColor = (hex: string): boolean => {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.55
  }

  const tagTextColor = (id: number): string => (isLightColor(tagColorFor(id)) ? '#1a1a1a' : 'white')

  const tagStyle = (id: number) => {
    const bg = tagColorFor(id)
    return { backgroundColor: bg, color: isLightColor(bg) ? '#1a1a1a' : 'white' }
  }

  return { tagStyle, tagTextColor }
}
