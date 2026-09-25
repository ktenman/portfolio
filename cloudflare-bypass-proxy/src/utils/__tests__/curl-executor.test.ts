import { execCurl, executeCurl } from '../curl-executor'

const mockExecFile = jest.fn()

jest.mock('child_process', () => ({
  execFile: (...args: unknown[]) => mockExecFile(...args),
}))

jest.mock('util', () => ({
  ...jest.requireActual('util'),
  promisify:
    (fn: (...args: unknown[]) => void) =>
    (...args: unknown[]) =>
      new Promise((resolve, reject) => {
        fn(...args, (error: Error | null, result: unknown) => {
          if (error) reject(error)
          else resolve(result)
        })
      }),
}))

function respond(stdout: string): void {
  mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
    callback(null, { stdout, stderr: '' })
  })
}

describe('Curl Executor', () => {
  beforeEach(() => {
    jest.resetAllMocks()
  })

  it('should separate the response body from HTTP status and content type', async () => {
    respond('{"result":"success"}\n200\napplication/json; charset=utf-8')

    const result = await executeCurl({
      url: 'https://example.com/api',
      timeout: 5000,
      maxBuffer: 512 * 1024,
    })

    expect(result).toEqual({
      stdout: '{"result":"success"}',
      duration: expect.any(Number),
      statusCode: 200,
      contentType: 'application/json; charset=utf-8',
    })
    expect(result.duration).toBeGreaterThanOrEqual(0)
    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      ['-s', '--write-out', '\n%{http_code}\n%{content_type}', 'https://example.com/api'],
      { timeout: 5000, maxBuffer: 512 * 1024 },
      expect.any(Function)
    )
  })

  it('should preserve the status and content type of an HTTP error', async () => {
    respond('<!DOCTYPE html><html>Not found</html>\n404\ntext/html; charset=utf-8')

    const result = await executeCurl({ url: 'https://example.com/missing' })

    expect(result).toEqual({
      stdout: '<!DOCTYPE html><html>Not found</html>',
      duration: expect.any(Number),
      statusCode: 404,
      contentType: 'text/html; charset=utf-8',
    })
  })

  it('should preserve trailing newlines in the response body', async () => {
    respond('first\n200\napplication/json\nlast\n\n\n200\ntext/plain')

    const result = await executeCurl({ url: 'https://example.com' })

    expect(result.stdout).toBe('first\n200\napplication/json\nlast\n\n')
  })

  it('should retain an empty response and missing content type', async () => {
    respond('\n204\n')

    expect(await executeCurl({ url: 'https://example.com' })).toEqual({
      stdout: '',
      duration: expect.any(Number),
      statusCode: 204,
      contentType: '',
    })
  })

  it('should keep the body-only wrapper compatible with existing consumers', async () => {
    respond('{"price":42}\n200\napplication/json')

    expect(await execCurl({ url: 'https://example.com' })).toBe('{"price":42}')
  })

  it('should pass POST data and headers without shell interpolation', async () => {
    respond('[]\n200\napplication/json')

    await executeCurl({
      url: 'https://example.com',
      method: 'post',
      body: '["instrument"]',
      headers: { 'Content-Type': 'application/json', accept: 'application/json' },
    })

    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      [
        '-s',
        '--write-out',
        '\n%{http_code}\n%{content_type}',
        '-X',
        'POST',
        '-H',
        'Content-Type: application/json',
        '-H',
        'accept: application/json',
        '-d',
        '["instrument"]',
        'https://example.com',
      ],
      { timeout: 10000, maxBuffer: 1024 * 1024 },
      expect.any(Function)
    )
  })

  it.each([
    ['curl timeout', { code: 28 }, true],
    ['node timeout', { code: null, killed: true, signal: 'SIGTERM' }, true],
    ['socket timeout', { code: 'ETIMEDOUT' }, true],
    ['DNS failure', { code: 6 }, false],
    ['buffer overflow', { code: 'ERR_CHILD_PROCESS_STDIO_MAXBUFFER', killed: true }, false],
  ])(
    'should classify %s without exposing the command or response',
    async (_name, fields, timedOut) => {
      const error = Object.assign(
        new Error('curl -H Authorization: secret https://private'),
        fields,
        {
          stdout: 'private response',
          stderr: 'private diagnostics',
        }
      )
      mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => callback(error, null))

      const failure = await executeCurl({ url: 'https://example.com' }).catch(error => error)

      expect(failure).toBeInstanceOf(Error)
      expect(failure).toHaveProperty('timedOut', timedOut)
      expect(failure.message).not.toMatch(/secret|private|Authorization/)
      expect(JSON.stringify(failure)).not.toMatch(/secret|private|Authorization/)
    }
  )

  it('should reject output without valid HTTP metadata', async () => {
    respond('private upstream body')

    await expect(executeCurl({ url: 'https://example.com' })).rejects.toThrow('curl request failed')
  })
})
