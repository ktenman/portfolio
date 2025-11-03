
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'BUY', 5, 114.4, '2025-11-03',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 4, 163.14, '2025-11-03',
        'TRADING212');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 3.15464994, 38.28, '2025-11-03',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 32, 38.28, '2025-11-03',
        'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 2, 38.2, '2025-11-03', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 32, 38.2, '2025-11-03', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 5, 162.68, '2025-11-03', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'BUY', 4, 114.24, '2025-11-03', 'LIGHTYEAR');
