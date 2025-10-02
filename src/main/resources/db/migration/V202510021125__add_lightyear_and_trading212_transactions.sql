INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 127, 35.38, '2025-10-02 07:54:43',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'BUY', 16, 109.65, '2025-10-02 08:01:46',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 16, 150.62, '2025-10-02 08:02:22',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 2, 150.62, '2025-10-02 08:02:41',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 1, 35.42, '2025-10-02 08:03:08',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 4, 35.43, '2025-10-02', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 1, 35.43, '2025-10-02', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 127, 35.38, '2025-10-02', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 1, 35.43, '2025-10-02', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 17, 150.70, '2025-10-02', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'BUY', 16, 109.54, '2025-10-02', 'LIGHTYEAR');