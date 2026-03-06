import type { PlatformDto } from '../models/generated/domain-models'

let platformDisplayNames: Record<string, string> = {}

export const setPlatformDisplayNames = (platforms: PlatformDto[]) => {
  platformDisplayNames = Object.fromEntries(platforms.map(p => [p.name, p.displayName]))
}

export const formatPlatformName = (platform: string): string =>
  platformDisplayNames[platform] || platform
