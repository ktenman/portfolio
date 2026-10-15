INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 1, 7.912, '2026-10-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VGLA:GER:EUR'), 'BUY', 2, 4.383, '2026-10-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 10, 61.32, '2026-10-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 633.27, 1, '2026-10-15', 'LHV', 0);
