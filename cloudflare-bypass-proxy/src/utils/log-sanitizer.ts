export function sanitizeLogInput(input: unknown): string {
  if (!input) return 'null'
  return String(input).replace(/[\n\r\t]/g, '_')
}
