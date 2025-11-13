const express = require('express');
const {execFile} = require('child_process');
const {promisify} = require('util');

const execFileAsync = promisify(execFile);
const app = express();

const PORT = process.env.PORT || 3000;
const CURL = process.env.CURL_BINARY || '/usr/local/bin/curl_ff117';
const URL = 'https://live.services.trading212.com/public-instrument-cache/v1/prices';

let requestCount = 0;
let errorCount = 0;

app.get('/health', (req, res) => res.json({status: 'healthy'}));

app.get('/prices', async (req, res) => {
  const {tickers} = req.query;

  if (!tickers) return res.status(400).json({error: 'Missing tickers parameter'});

  requestCount++;

  try {
    const start = Date.now();
    const {stdout} = await execFileAsync(CURL, ['-s', `${URL}?tickers=${tickers}`], {
      timeout: 10000,
      maxBuffer: 1024 * 1024
    });
    console.log(`[${new Date().toISOString()}] Fetched ${tickers.split(',').length} tickers in ${Date.now() - start}ms`);
    res.json(JSON.parse(stdout));
  } catch (error) {
    errorCount++;
    console.error(`[${new Date().toISOString()}] Error:`, error.message);
    res.status(500).json({error: 'Failed to fetch prices', message: error.message});
  }
});

app.get('/metrics', (req, res) => res.json({
  requests_total: requestCount,
  errors_total: errorCount,
  error_rate: requestCount > 0 ? `${(errorCount / requestCount * 100).toFixed(2)}%` : '0%'
}));

app.listen(PORT, '0.0.0.0', () => console.log(`[${new Date().toISOString()}] Trading212 proxy listening on port ${PORT}`));
