import { fetchPublicHtml, fetchPublicImage } from '../image-utils'

const mockExecFile = jest.fn()
const mockLookup = jest.fn()

jest.mock('child_process', () => ({ execFile: (...args: unknown[]) => mockExecFile(...args) }))
jest.mock('dns/promises', () => ({ lookup: (...args: unknown[]) => mockLookup(...args) }))

const curlAnswers = (stdout: string | Buffer) =>
  mockExecFile.mockImplementation((_command, _args, _options, callback) =>
    callback(null, { stdout: Buffer.isBuffer(stdout) ? stdout : Buffer.from(stdout) })
  )

describe('Public logo page downloads', () => {
  beforeEach(() => {
    jest.resetAllMocks()
    mockLookup.mockResolvedValue([{ address: '93.184.215.14', family: 4 }])
    curlAnswers('<html>NVIDIA logo</html>\n200\t\ttext/html; charset=utf-8')
  })

  it('downloads HTML with the validated address pinned to the hostname', async () => {
    const page = await fetchPublicHtml('https://example.com/logo')

    expect({ page, arguments: mockExecFile.mock.calls[0][1] }).toEqual({
      page: { url: 'https://example.com/logo', html: '<html>NVIDIA logo</html>' },
      arguments: expect.arrayContaining([
        '--resolve',
        'example.com:443:93.184.215.14',
        '--max-filesize',
        '2097152',
      ]),
    })
  })

  it('keeps glob-like URL characters bound to the validated literal destination', async () => {
    const url = 'https://{a,b}.example.com/logo?size[0]=32'

    await fetchPublicHtml(url)

    expect(mockLookup).toHaveBeenCalledWith('{a,b}.example.com', { all: true })
    expect(mockExecFile.mock.calls[0][1]).toEqual(
      expect.arrayContaining(['--globoff', '{a,b}.example.com:443:93.184.215.14', url])
    )
  })

  it.each([
    'http://127.0.0.1/logo',
    'http://169.254.169.254/logo',
    'http://10.0.0.1/logo',
    'http://[::1]/logo',
    'http://[::ffff:127.0.0.1]/logo',
    'http://localhost/logo',
    'https://example.com:8443/logo',
    'https://user:password@example.com/logo',
    'file:///tmp/logo',
  ])('refuses a private or unsupported URL %s', async url => {
    await expect(fetchPublicHtml(url)).rejects.toThrow('Logo page URL must be public')
  })

  it('rejects a public-looking hostname that resolves to a private address', async () => {
    mockLookup.mockResolvedValue([{ address: '192.168.1.10', family: 4 }])

    await expect(fetchPublicHtml('https://example.com/logo')).rejects.toThrow(
      'must resolve to a public address'
    )
  })

  it('rejects DNS answers mixing public and private addresses', async () => {
    mockLookup.mockResolvedValue([
      { address: '93.184.215.14', family: 4 },
      { address: '10.0.0.5', family: 4 },
    ])

    await expect(fetchPublicHtml('https://example.com/logo')).rejects.toThrow(
      'must resolve to a public address'
    )
  })

  it('validates redirects before requesting the next destination', async () => {
    curlAnswers('\n302\thttp://169.254.169.254/metadata\ttext/html')

    await expect(fetchPublicHtml('https://example.com/logo')).rejects.toThrow(
      'Logo page URL must be public'
    )
  })

  it('returns the final URL after a public redirect', async () => {
    mockExecFile.mockImplementationOnce((_command, _args, _options, callback) =>
      callback(null, {
        stdout: Buffer.from('\n301\thttps://example.com/brands/nvidia/\ttext/html'),
      })
    )

    expect(await fetchPublicHtml('https://example.com/logo')).toEqual({
      url: 'https://example.com/brands/nvidia/',
      html: '<html>NVIDIA logo</html>',
    })
  })

  it('stops redirect loops', async () => {
    curlAnswers('\n302\thttps://example.com/logo\ttext/html')

    await expect(fetchPublicHtml('https://example.com/logo')).rejects.toThrow(
      'redirected too many times'
    )
  })

  it.each(['202\t\ttext/html', '403\t\ttext/html', '200\t\timage/png'])(
    'does not treat response %s as usable HTML',
    async metadata => {
      curlAnswers(`body\n${metadata}`)

      await expect(fetchPublicHtml('https://example.com/logo')).rejects.toThrow(
        'Logo page returned HTTP'
      )
    }
  )

  it('preserves binary image bytes and pins the address during image downloads', async () => {
    const bytes = Buffer.from('89504e470d0a1a0aff000a', 'hex')
    curlAnswers(Buffer.concat([bytes, Buffer.from('\n200\t\timage/png')]))

    const image = await fetchPublicImage('https://example.com/nvidia-logo.png')

    expect(image.content).toEqual(bytes)
    expect(image.type).toBe('image/png')
    expect(mockExecFile.mock.calls[0][1]).toEqual(
      expect.arrayContaining(['--resolve', 'example.com:443:93.184.215.14'])
    )
    expect(mockExecFile.mock.calls[0][2].encoding).toBe('buffer')
  })

  it('rejects private DNS answers before downloading an extracted image', async () => {
    mockLookup.mockResolvedValue([{ address: '127.0.0.1', family: 4 }])

    await expect(fetchPublicImage('http://127.0.0.1.nip.io/nvidia-logo.png')).rejects.toThrow(
      'must resolve to a public address'
    )
    expect(mockExecFile).not.toHaveBeenCalled()
  })

  it('rejects an image redirect to a private destination before requesting it', async () => {
    curlAnswers('\n302\thttp://169.254.169.254/metadata\ttext/html')

    await expect(fetchPublicImage('https://example.com/nvidia-logo.png')).rejects.toThrow(
      'Logo page URL must be public'
    )
    expect(mockExecFile).toHaveBeenCalledTimes(1)
  })

  it('validates the resolved address of an image redirect target', async () => {
    mockLookup
      .mockResolvedValueOnce([{ address: '93.184.215.14', family: 4 }])
      .mockResolvedValueOnce([{ address: '10.0.0.1', family: 4 }])
    curlAnswers('\n302\thttps://images.example/logo.png\timage/png')

    await expect(fetchPublicImage('https://example.com/nvidia-logo.png')).rejects.toThrow(
      'must resolve to a public address'
    )
    expect(mockExecFile).toHaveBeenCalledTimes(1)
  })

  it('rejects HTML served by an image URL', async () => {
    await expect(fetchPublicImage('https://example.com/nvidia-logo.png')).rejects.toThrow(
      'Logo page returned HTTP 200 (text/html; charset=utf-8)'
    )
  })

  it.each([
    ['89504e470d0a1a0a', 'image/png'],
    ['ffd8ff', 'image/jpeg'],
    ['524946460000000057454250', 'image/webp'],
  ])('recognizes image bytes %s served as generic binary data', async (hex, type) => {
    const bytes = Buffer.from(hex, 'hex')
    curlAnswers(Buffer.concat([bytes, Buffer.from('\n200\t\tapplication/octet-stream')]))

    const image = await fetchPublicImage('https://example.com/nvidia-logo.png')

    expect(image.content).toEqual(bytes)
    expect(image.type).toBe(type)
  })

  it('rejects non-image bytes even when the response claims to be an image', async () => {
    curlAnswers('<html>challenge</html>\n200\t\timage/png')

    await expect(fetchPublicImage('https://example.com/nvidia-logo.png')).rejects.toThrow(
      'Logo image has an unsupported format'
    )
  })
})
