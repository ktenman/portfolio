INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2892.55, 1.00, '2026-02-25', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 1, 146.88, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 2, 76.89, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 3, 59.54, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 3, 57.69, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 5, 49.17, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 16, 38.00, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 6, 34.56, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 36, 14.421, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 25, 8.817, '2026-02-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 67, 6.592, '2026-02-25', 'LHV', 0);
