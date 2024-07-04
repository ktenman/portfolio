-- Sample data insertion
INSERT INTO instrument (symbol, name, instrument_category, base_currency)
VALUES ('QDVE.DEX', 'iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)', 'ETF', 'EUR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE.DEX'), 'BUY', 3.37609300, 29.62003713, '2024-07-01');
