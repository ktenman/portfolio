INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 20.875497441, 14.07, '2025-11-14',
        'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 5.772844089, 70.04, '2025-11-14',
        'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 6.905311624, 149.86, '2025-11-14',
        'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 48.47210219, 35.31, '2025-11-14',
        'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 20.869565217, 14.08, '2025-11-14',
        'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 5.770372484, 70.07, '2025-11-14',
        'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 6.902548025, 149.92, '2025-11-14',
        'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 48.410408711, 35.35, '2025-11-14',
        'LIGHTYEAR');
