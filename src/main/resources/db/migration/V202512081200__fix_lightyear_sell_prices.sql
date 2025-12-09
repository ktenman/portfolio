-- Fix Lightyear sell prices to match Capital Gains Statement 2025-01-01 to 2025-12-07
-- Prices are calculated as: proceeds / quantity to get exact match with Lightyear statement

-- VWCE 2025-12-01: 0.294359331 units, proceeds 42.27
UPDATE portfolio_transaction pt
SET price = 143.6000002324
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'VWCE'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-12-01'
  AND pt.quantity = 0.294359331;

-- CSX5 2025-12-01: 0.265054638 units, proceeds 57.00
UPDATE portfolio_transaction pt
SET price = 215.0500003701
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'CSX5'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-12-01'
  AND pt.quantity = 0.265054638;

-- WTAI 2025-12-01: 11.029507270 units, proceeds 781.22
UPDATE portfolio_transaction pt
SET price = 70.8300000060
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'WTAI'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-12-01'
  AND pt.quantity = 11.029507270;

-- XAIX 2025-12-01: 2.936050364 units, proceeds 443.05
UPDATE portfolio_transaction pt
SET price = 150.9000000247
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'XAIX'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-12-01'
  AND pt.quantity = 2.936050364;

-- QDVE 2025-12-01: 6.519842561 units, proceeds 232.37
UPDATE portfolio_transaction pt
SET price = 35.6404311647
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'QDVE'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-12-01'
  AND pt.quantity = 6.519842561;

-- VNRT 2025-12-01: 40.074120429 units, proceeds 5724.99
UPDATE portfolio_transaction pt
SET price = 142.8600288344
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'VNRT'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-12-01'
  AND pt.quantity = 40.074120429;

-- WBIT 2025-11-26: 2.987030377 units, proceeds 55.32
UPDATE portfolio_transaction pt
SET price = 18.5200660917
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'WBIT'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-26'
  AND pt.quantity = 2.987030377;

-- CSX5 2025-11-26: 6.741718786 units, proceeds 1455.20
UPDATE portfolio_transaction pt
SET price = 215.8500000062
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'CSX5'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-26'
  AND pt.quantity = 6.741718786;

-- VNRT 2025-11-26: 9.925879571 units, proceeds 1427.54
UPDATE portfolio_transaction pt
SET price = 143.8200000100
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'VNRT'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-26'
  AND pt.quantity = 9.925879571;

-- WTAI 2025-11-26: 20.804034178 units, proceeds 1485.20
UPDATE portfolio_transaction pt
SET price = 71.3900000016
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'WTAI'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-26'
  AND pt.quantity = 20.804034178;

-- XAIX 2025-11-26: 8.977722119 units, proceeds 1362.10
UPDATE portfolio_transaction pt
SET price = 151.7200000117
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'XAIX'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-26'
  AND pt.quantity = 8.977722119;

-- QDVE 2025-11-26: 38.802054414 units, proceeds 1397.65
UPDATE portfolio_transaction pt
SET price = 36.0200000002
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'QDVE'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-26'
  AND pt.quantity = 38.802054414;

-- SPYL 2025-11-24: 227 units, proceeds 3236.11
UPDATE portfolio_transaction pt
SET price = 14.2559911894
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'SPYL'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-24'
  AND pt.quantity = 227.000000000;

-- SPYL 2025-11-24: 0.968735440 units, proceeds 13.82
UPDATE portfolio_transaction pt
SET price = 14.2660208653
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'SPYL'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-24'
  AND pt.quantity = 0.968735440;

-- XAIX 2025-11-24: 27 units, proceeds 4068.90
UPDATE portfolio_transaction pt
SET price = 150.7000000000
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'XAIX'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-24'
  AND pt.quantity = 27.000000000;

-- QDVE 2025-11-24: 454 units, proceeds 16253.20
UPDATE portfolio_transaction pt
SET price = 35.8000000000
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'QDVE'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-24'
  AND pt.quantity = 454.000000000;

-- XAIX 2025-11-24: 16 units, proceeds 2384.00
UPDATE portfolio_transaction pt
SET price = 149.0000000000
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'XAIX'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-24'
  AND pt.quantity = 16.000000000;

-- SPYL 2025-11-12: 59.2 units, proceeds 860.53
UPDATE portfolio_transaction pt
SET price = 14.5359797297
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'SPYL'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-12'
  AND pt.quantity = 59.200000000;

-- WTAI 2025-11-12: 11.88 units, proceeds 893.85
UPDATE portfolio_transaction pt
SET price = 75.2398989899
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'WTAI'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-12'
  AND pt.quantity = 11.880000000;

-- VUAA 2025-11-05: 20 units, proceeds 2265.70
UPDATE portfolio_transaction pt
SET price = 113.2850000000
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'VUAA'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-11-05'
  AND pt.quantity = 20.000000000;

-- QDVE 2025-01-02: 42 units, proceeds 1381.06
UPDATE portfolio_transaction pt
SET price = 32.8823809524
FROM instrument i
WHERE pt.instrument_id = i.id
  AND pt.platform = 'LIGHTYEAR'
  AND i.symbol = 'QDVE'
  AND pt.transaction_type = 'SELL'
  AND pt.transaction_date = '2025-01-02'
  AND pt.quantity = 42.000000000;
