INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 96.114350136, 1193.55 / 96.114350136, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 215.430267062, 1742.40 / 215.430267062, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'SELL', 75.407355382, 590.52 / 75.407355382, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 43.409772348, 2345.43 / 43.409772348, '2026-05-22', 'LIGHTYEAR', 0);
