import express from 'express'
import { listen } from './listen'

describe('listen', () => {
  it('should bind the IPv4 loopback address so no other process can share the port', async () => {
    const server = await listen(express())

    expect(server.address()).toMatchObject({ address: '127.0.0.1' })
  })
})
