-- Sample data insertion
INSERT INTO instrument (symbol, name, instrument_category, base_currency)
VALUES ('QDVE.DEX', 'iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)', 'ETF', 'EUR');
INSERT INTO instrument (symbol, name, instrument_category, base_currency)
VALUES ('EXXT.DEX', 'iShares NASDAQ-100® UCITS ETF (DE)', 'ETF', 'EUR');
INSERT INTO instrument (symbol, name, instrument_category, base_currency)
VALUES ('NQSE', 'iShares NASDAQ 100 UCITS ETF', 'ETF', 'EUR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE.DEX'), 'BUY', 3.37609300, 29.62003713, '2024-07-01');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EXXT.DEX'), 'BUY', 1, 180.96, '2024-07-04');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'NQSE'), 'BUY', 8.2304526749, 12.15, '2024-07-04');
