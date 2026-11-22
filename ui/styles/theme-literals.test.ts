/// <reference types="node" />
import { globSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const UI = join(import.meta.dirname, '..')

const sources = globSync('**/*.{vue,css,ts}', {
  cwd: UI,
  exclude: ['**/*.test.ts', 'tests/**', 'models/generated/**', 'styles/theme.css'],
})

const ALLOWED: Record<string, string> = {
  'constants/chart-colors.ts': 'canvas palette, checked by its own test',
  'constants/editor-theme.ts':
    "Monaco's defineTheme takes hex only; a test pins each hex to its token",
  'composables/use-theme.ts':
    "the browser bar's theme-color, the same hex as the page background in index.html",
  'components/poff/poff-film-media.vue': 'white text and black scrims over photos',
  'styles/components/forms.css': 'chevron strokes inside data URIs, which no token can reach',
}

const LITERAL =
  /(?<!&)(?:#|%23)[\da-fA-F]{3,8}(?![\w-])|(?:rgba?|hsla?|oklch|oklab)\(|(?:(?<![\w-])|-)(?:white|black)(?![\w-])/g

const literals = (file: string) =>
  readFileSync(join(UI, file), 'utf8')
    .split('\n')
    .flatMap((line, index) =>
      [...line.matchAll(LITERAL)].map(([match]) => `ui/${file}:${index + 1} ${match}`)
    )

describe('theme literals', () => {
  it('no file outside the theme declares a literal colour', () => {
    expect(sources.filter(file => !(file in ALLOWED)).flatMap(literals)).toEqual([])
  })
})
