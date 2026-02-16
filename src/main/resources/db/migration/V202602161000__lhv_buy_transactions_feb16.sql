INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 1, 75.22, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 1, 56.48, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 1, 48.435, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 3, 37.305, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 1, 33.335, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 19, 6.43, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 2, 8.56, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 14, 6.443, '2026-02-16', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 2, 14.25, '2026-02-16', 'LHV', 0);
