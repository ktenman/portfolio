import type { editor } from 'monaco-editor'

export const EDITOR_TOKEN_HEX = {
  surface: '#211d1a',
  hairline: '#342f2b',
  'hairline-strong': '#47413c',
  ink: '#eae7e2',
  'ink-soft': '#a5a199',
  brass: '#dbb06b',
  'brass-wash': '#3a2b16',
  gain: '#6bc987',
  notice: '#7fb6ee',
}

export const EDITOR_DARK_THEME = 'portfolio-dark'

export const EDITOR_THEME: editor.IStandaloneThemeData = {
  base: 'vs-dark',
  inherit: true,
  rules: [
    { token: 'string.key.json', foreground: EDITOR_TOKEN_HEX.brass },
    { token: 'string.value.json', foreground: EDITOR_TOKEN_HEX.gain },
    { token: 'number.json', foreground: EDITOR_TOKEN_HEX.notice },
  ],
  colors: {
    'editor.background': EDITOR_TOKEN_HEX.surface,
    'editor.foreground': EDITOR_TOKEN_HEX.ink,
    'editorLineNumber.foreground': EDITOR_TOKEN_HEX['ink-soft'],
    'editorLineNumber.activeForeground': EDITOR_TOKEN_HEX.ink,
    'editor.selectionBackground': EDITOR_TOKEN_HEX['brass-wash'],
    'editorCursor.foreground': EDITOR_TOKEN_HEX.brass,
    'editorIndentGuide.background1': EDITOR_TOKEN_HEX.hairline,
    'editorIndentGuide.activeBackground1': EDITOR_TOKEN_HEX['hairline-strong'],
  },
}
