import { executeCurl } from '../curl-executor'

const mockExecFile = jest.fn()

jest.mock('child_process', () => ({
  execFile: (...args: any[]) => mockExecFile(...args),
}))

jest.mock('util', () => ({
  promisify: (fn: any) => {
    return jest.fn((...args) => {
      return new Promise((resolve, reject) => {
        fn(...args, (error: Error | null, result: any) => {
          if (error) reject(error)
          else resolve(result)
        })
      })
    })
  },
}))

describe('Curl Executor', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('should execute curl with correct arguments', async () => {
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
      callback(null, { stdout: '{"result": "success"}', stderr: '' })
    })

    const result = await executeCurl({
      url: 'https://example.com/api',
      timeout: 5000,
      maxBuffer: 512 * 1024,
    })

    expect(result.stdout).toBe('{"result": "success"}')
    expect(result.duration).toBeGreaterThanOrEqual(0)
    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      ['-s', 'https://example.com/api'],
      {
        timeout: 5000,
        maxBuffer: 512 * 1024,
      },
      expect.any(Function)
    )
  })

  it('should include headers when provided', async () => {
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
      callback(null, { stdout: 'response', stderr: '' })
    })

    await executeCurl({
      url: 'https://example.com',
      headers: {
        'user-agent': 'test-agent',
        accept: 'application/json',
      },
    })

    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      [
        '-s',
        '-H',
        'user-agent: test-agent',
        '-H',
        'accept: application/json',
        'https://example.com',
      ],
      expect.any(Object),
      expect.any(Function)
    )
  })

  it('should use default timeout and maxBuffer when not specified', async () => {
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
      callback(null, { stdout: 'data', stderr: '' })
    })

    await executeCurl({ url: 'https://example.com' })

    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(Array),
      {
        timeout: 10000,
        maxBuffer: 1024 * 1024,
      },
      expect.any(Function)
    )
  })

  it('should handle execution errors', async () => {
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
      callback(new Error('Network timeout'), null)
    })

    await expect(executeCurl({ url: 'https://example.com' })).rejects.toThrow(
      'curl execution failed: Network timeout'
    )
  })

  it('should measure execution duration', async () => {
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
      setTimeout(() => callback(null, { stdout: 'ok', stderr: '' }), 100)
    })

    const result = await executeCurl({ url: 'https://example.com' })

    expect(result.duration).toBeGreaterThanOrEqual(100)
  })
})
