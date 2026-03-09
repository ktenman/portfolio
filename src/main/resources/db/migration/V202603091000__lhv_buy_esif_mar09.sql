INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 31, 13.53, '2026-03-09', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 2, 13.522, '2026-03-09', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 447.50, 1, '2026-03-09', 'LHV', 0);
