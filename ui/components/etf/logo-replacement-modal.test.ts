import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { ApiError } from '../../models/api-error'
import { logoService, type LogoCandidateDto } from '../../services/api'
import LogoReplacementModal from './logo-replacement-modal.vue'

const UUID = '550e8400-e29b-41d4-a716-446655440000'

const CANDIDATE: LogoCandidateDto = {
  thumbnailUrl: 'https://example.com/tuleva.png',
  title: 'Tuleva logo',
  index: 0,
  imageDataUrl: 'data:image/png;base64,AAAA',
}

const UNAVAILABLE = new ApiError(503, 'Logo search is unavailable right now', '')

enableAutoUnmount(afterEach)

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(logoService, 'getCandidates').mockResolvedValue([])
})

const openModal = async (holdingUuid: string | null = UUID) => {
  const wrapper = mount(LogoReplacementModal, {
    props: { modelValue: false, holdingUuid, holdingName: 'Tuleva Maailma Aktsiad õ' },
    attachTo: document.body,
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  return wrapper
}

const choose = async (wrapper: Awaited<ReturnType<typeof openModal>>, file: File) => {
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file] })
  await input.trigger('change')
  await flushPromises()
}

const png = () => new File(['õ'], 'tuleva.png', { type: 'image/png' })

describe('LogoReplacementModal upload', () => {
  it('offers an upload when the search found no candidates', async () => {
    const wrapper = await openModal()
    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
  })

  it('offers an upload next to the found candidates', async () => {
    vi.spyOn(logoService, 'getCandidates').mockResolvedValue([CANDIDATE])
    const wrapper = await openModal()
    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
  })

  it('offers no upload when there is no record to save the logo to', async () => {
    vi.spyOn(logoService, 'searchByName').mockResolvedValue([])
    const wrapper = await openModal(null)
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
  })

  it('accepts only PNG, JPEG and WebP images', async () => {
    const wrapper = await openModal()
    expect(wrapper.find('input[type="file"]').attributes('accept')).toBe(
      'image/png,image/jpeg,image/webp'
    )
  })

  it('uploads the chosen image to the logo owner', async () => {
    const upload = vi.spyOn(logoService, 'uploadLogo').mockResolvedValue({
      success: true,
      message: 'Logo replaced successfully',
    })
    const file = png()
    await choose(await openModal(), file)
    expect(upload).toHaveBeenCalledWith(UUID, file)
  })

  it('emits replaced once the upload is stored', async () => {
    vi.spyOn(logoService, 'uploadLogo').mockResolvedValue({
      success: true,
      message: 'Logo replaced successfully',
    })
    const wrapper = await openModal()
    await choose(wrapper, png())
    expect(wrapper.emitted('replaced')).toEqual([[UUID]])
  })

  it('shows why the server refused the image', async () => {
    vi.spyOn(logoService, 'uploadLogo').mockRejectedValue(
      new ApiError(400, 'Logo must be a PNG or JPEG up to 4:1 or a square WebP, 32 to 4096 px', '')
    )
    const wrapper = await openModal()
    await choose(wrapper, png())
    expect(wrapper.text()).toContain(
      'Logo must be a PNG or JPEG up to 4:1 or a square WebP, 32 to 4096 px'
    )
  })
})

describe('LogoReplacementModal search', () => {
  it('searches again when the search found no candidates', async () => {
    const wrapper = await openModal()
    await wrapper.get('[data-search-again]').trigger('click')
    expect(logoService.getCandidates).toHaveBeenCalledTimes(2)
  })

  it('says search is unavailable when the server answers 503', async () => {
    vi.spyOn(logoService, 'getCandidates').mockRejectedValue(UNAVAILABLE)
    const wrapper = await openModal()
    expect(wrapper.text()).toContain(UNAVAILABLE.message)
    expect(wrapper.text()).not.toContain('No logo candidates found')
    expect(wrapper.find('[data-search-again]').exists()).toBe(true)
  })

  it('says the same when a search by name is unavailable', async () => {
    vi.spyOn(logoService, 'searchByName').mockRejectedValue(UNAVAILABLE)
    const wrapper = await openModal(null)
    expect(wrapper.text()).toContain(UNAVAILABLE.message)
  })

  it('keeps the alert for other failures', async () => {
    vi.spyOn(logoService, 'getCandidates').mockRejectedValue(new ApiError(500, 'õ', ''))
    const wrapper = await openModal()
    expect(wrapper.text()).toContain('Failed to load logo candidates')
    expect(wrapper.find('[data-search-again]').exists()).toBe(false)
  })

  it('clears the message when Search again finds candidates', async () => {
    vi.spyOn(logoService, 'getCandidates')
      .mockRejectedValueOnce(UNAVAILABLE)
      .mockResolvedValueOnce([CANDIDATE])
    const wrapper = await openModal()
    await wrapper.get('[data-search-again]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain(UNAVAILABLE.message)
    expect(wrapper.find('.candidate-image').exists()).toBe(true)
  })

  it('says no candidates when Search again finds none after an outage', async () => {
    vi.spyOn(logoService, 'getCandidates')
      .mockRejectedValueOnce(UNAVAILABLE)
      .mockResolvedValueOnce([])
    const wrapper = await openModal()
    await wrapper.get('[data-search-again]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No logo candidates found')
  })

  it('says no candidates when a 503 has no message', async () => {
    vi.spyOn(logoService, 'getCandidates').mockRejectedValue(new ApiError(503, '', ''))
    const wrapper = await openModal()
    expect(wrapper.text()).toContain('No logo candidates found')
  })
})
