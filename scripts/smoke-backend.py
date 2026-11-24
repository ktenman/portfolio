#!/usr/bin/env python3
import base64
import json
import struct
import sys
import urllib.error
import urllib.request
import zlib

BOUNDARY = 'smoke-backend'
JPEG = base64.b64decode('/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDABALDA4MChAODQ4SERATGCgaGBYWGDEjJR0oOjM9PDkzODdASFxOQERXRTc4UG1RV19iZ2hnPk1xeXBkeFxlZ2P/2wBDARESEhgVGC8aGi9jQjhCY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2P/wAARCAAgAEADASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFgEBAQEAAAAAAAAAAAAAAAAAAAQF/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8AmgNBCAAAAAAAAAAAAAA//9k=')
READS = [
    '/actuator/health',
    '/actuator/prometheus',
    '/api/build-info',
    '/api/enums',
    '/api/instruments',
    '/api/instruments?platforms=LIGHTYEAR&period=1W',
    '/api/transactions',
    '/api/transactions?platforms=LIGHTYEAR&fromDate=2025-01-01&untilDate=2025-12-31',
    '/api/transactions/platforms',
    '/api/portfolio-summary/current',
    '/api/portfolio-summary/historical?page=0&size=30',
    '/api/portfolio-summary/series',
    '/api/portfolio-summary/intraday',
    '/api/portfolio-summary/benchmark',
    '/api/portfolio-summary/range-change',
    '/api/portfolio-summary/xirr-windows',
    '/api/portfolio-summary/annual-windows',
    '/api/etf-breakdown',
    '/api/etf-breakdown/benchmark?symbol=VUAA:GER:EUR',
    '/api/etf-breakdown/diagnostic',
    '/api/diversification/available-etfs',
    '/api/diversification/config',
    '/api/radio',
    '/api/radio/programmes',
    '/api/radio/schedule',
    '/api/radio/station',
    '/api/radio/digests',
    '/api/radio/player',
    '/api/poff/enabled',
    '/api/preferences',
    '/api/health/memory',
    '/api/health/system',
    '/api/health/threads',
    '/api/health/circuit-breakers',
    '/api-docs',
    '/swagger-ui/index.html',
]


def call(base, method, path, body=None, kind='application/json'):
    data = json.dumps(body).encode() if body is not None and kind == 'application/json' else body
    request = urllib.request.Request(base + path, data=data, method=method, headers={'X-User-Id': 'smoke', 'Content-Type': kind})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            text = response.read()
            return response.status, json.loads(text) if text and 'json' in response.headers.get('Content-Type', '') else None
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(errors='replace')[:1500]


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def png(width, height):
    row = b'\x00' + b'\x33\x66\x99' * width
    header = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(row * height)) + chunk(b'IEND', b'')


def upload(base, uuid, kind, image):
    head = f'--{BOUNDARY}\r\nContent-Disposition: form-data; name="file"; filename="logo"\r\nContent-Type: {kind}\r\n\r\n'
    body = head.encode() + image + f'\r\n--{BOUNDARY}--\r\n'.encode()
    return call(base, 'POST', f'/api/logos/{uuid}', body, f'multipart/form-data; boundary={BOUNDARY}')[0]


def trades(base, instrument_id):
    trade = {'instrumentId': instrument_id, 'transactionType': 'BUY', 'quantity': 2, 'price': 10.5, 'transactionDate': '2025-04-01', 'platform': 'LIGHTYEAR'}
    status, saved = call(base, 'POST', '/api/transactions', trade)
    yield 'POST /api/transactions', status, 201
    if status != 201:
        return
    trade_id = saved['id']
    yield 'GET /api/transactions/id', call(base, 'GET', f'/api/transactions/{trade_id}')[0], 200
    yield 'PUT /api/transactions/id', call(base, 'PUT', f'/api/transactions/{trade_id}', {**trade, 'quantity': 3})[0], 200
    yield 'GET /api/instruments after trade', call(base, 'GET', '/api/instruments')[0], 200
    yield 'GET /api/portfolio-summary/current after trade', call(base, 'GET', '/api/portfolio-summary/current')[0], 200
    yield 'POST /api/portfolio-summary/recalculate', call(base, 'POST', '/api/portfolio-summary/recalculate')[0], 200
    yield 'DELETE /api/transactions/id', call(base, 'DELETE', f'/api/transactions/{trade_id}')[0], 204


def writes(base):
    instrument = {'symbol': 'QDVE:GER:EUR', 'name': 'Suitsutest Õ', 'category': 'ETF', 'baseCurrency': 'EUR', 'providerName': 'MANUAL', 'currentPrice': 10}
    yield 'POST /api/instruments without a symbol', call(base, 'POST', '/api/instruments', {**instrument, 'symbol': ' '})[0], 400
    yield 'GET /api/vehicle/info without a plate', call(base, 'GET', '/api/vehicle/info?plateNumber=')[0], 400
    status, created = call(base, 'POST', '/api/instruments', instrument)
    yield 'POST /api/instruments', status, 200
    if status != 200:
        return
    instrument_id = created['id']
    yield 'PUT /api/instruments/id', call(base, 'PUT', f'/api/instruments/{instrument_id}', {**instrument, 'currentPrice': 11})[0], 200
    logo = f"/api/logos/{created['uuid']}"
    yield 'GET /api/logos/uuid before upload', call(base, 'GET', logo)[0], 404
    yield 'POST /api/logos/uuid', upload(base, created['uuid'], 'image/png', png(64, 32)), 200
    yield 'GET /api/logos/uuid', call(base, 'GET', logo)[0], 200
    yield 'POST /api/logos/uuid as JPEG', upload(base, created['uuid'], 'image/jpeg', JPEG), 200
    yield 'GET /api/calculator', call(base, 'GET', '/api/calculator')[0], 200
    allocations = {'allocations': [{'instrumentId': instrument_id, 'percentage': 100}]}
    yield 'POST /api/diversification/calculate', call(base, 'POST', '/api/diversification/calculate', allocations)[0], 200
    yield from trades(base, instrument_id)
    yield 'DELETE /api/instruments/id', call(base, 'DELETE', f'/api/instruments/{instrument_id}')[0], 204
    yield 'PUT /api/preferences', call(base, 'PUT', '/api/preferences', {'theme': 'DARK'})[0], 204
    yield 'GET /api/preferences', call(base, 'GET', '/api/preferences')[0], 200
    yield 'DELETE /api/etf-breakdown/cache', call(base, 'DELETE', '/api/etf-breakdown/cache')[0], 200


def main():
    base = sys.argv[1].rstrip('/')
    management = sys.argv[2].rstrip('/') if len(sys.argv) > 2 else base
    failed = []
    for _ in range(2):
        for path in READS:
            status, body = call(management if path.startswith('/actuator/') else base, 'GET', path)
            if status >= 500:
                failed.append(f'GET {path} = {status} {body}')
    failed += [f'{name} = {status}, expected {expected}' for name, status, expected in writes(base) if status != expected]
    print('\n'.join(failed) or f'smoke: {len(READS)} reads twice and the write round passed')
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
