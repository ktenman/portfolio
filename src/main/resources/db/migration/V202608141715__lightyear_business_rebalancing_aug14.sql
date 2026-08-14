INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'BUY', 12.881380753, 1231.46 / 12.881380753, '2026-08-14', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 10.884359732, 1231.46 / 10.884359732, '2026-08-14', 'LIGHTYEAR_BUSINESS', 0);
