INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 3, 146.62, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 7, 75.57, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 6, 56.61, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 1, 230.70, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 2, 48.985, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 8, 37.715, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 16, 32.935, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 1, 8.423, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 61, 6.498, '2026-02-11', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2872.19, 1.00, '2026-02-11', 'SWEDBANK', 0);
