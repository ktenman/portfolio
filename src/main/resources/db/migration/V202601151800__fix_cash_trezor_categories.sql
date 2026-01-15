UPDATE instrument SET instrument_category = 'CASH' WHERE symbol = 'CASH' AND instrument_category = 'ETF';
UPDATE instrument SET instrument_category = 'CRYPTO' WHERE symbol = 'TREZOR' AND instrument_category = 'ETF';
