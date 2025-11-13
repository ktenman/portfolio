const express = require('express')
const { execFile } = require('child_process')
const { promisify } = require('util')

const execFileAsync = promisify(execFile)
const app = express()

const PORT = process.env.PORT || 3000
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117'
const TRADING212_URL = 'https://live.services.trading212.com/public-instrument-cache/v1/prices'
const WISDOMTREE_BASE_URL = 'https://www.wisdomtree.eu/en-gb/global/etf-details/modals/all-holdings'

app.get('/health', (req, res) => res.json({ status: 'healthy' }))

app.get('/prices', async (req, res) => {
  const { tickers } = req.query

  if (!tickers) return res.status(400).json({ error: 'Missing tickers parameter' })

  try {
    const start = Date.now()
    const { stdout } = await execFileAsync(CURL, ['-s', `${TRADING212_URL}?tickers=${tickers}`], {
      timeout: 10000,
      maxBuffer: 1024 * 1024,
    })
    console.log(
      `[${new Date().toISOString()}] Fetched ${tickers.split(',').length} tickers in ${Date.now() - start}ms`
    )
    res.json(JSON.parse(stdout))
  } catch (error) {
    console.error(`[${new Date().toISOString()}] Error:`, error.message)
    res.status(500).json({ error: 'Failed to fetch prices', message: error.message })
  }
})

app.get('/wisdomtree/holdings/:etfId', async (req, res) => {
  const { etfId } = req.params

  if (!etfId) return res.status(400).json({ error: 'Missing etfId parameter' })

  try {
    const start = Date.now()
    const url = `${WISDOMTREE_BASE_URL}?id={${etfId}}`
    const { stdout } = await execFileAsync(CURL, ['-s', url], {
      timeout: 15000,
      maxBuffer: 2 * 1024 * 1024,
    })
    console.log(
      `[${new Date().toISOString()}] Fetched WisdomTree holdings for ${etfId} in ${Date.now() - start}ms`
    )
    res.type('html').send(stdout)
  } catch (error) {
    console.error(`[${new Date().toISOString()}] WisdomTree Error:`, error.message)
    res.status(500).json({ error: 'Failed to fetch holdings', message: error.message })
  }
})

app.listen(PORT, '0.0.0.0', () =>
  console.log(`[${new Date().toISOString()}] Proxy listening on port ${PORT}`)
)
