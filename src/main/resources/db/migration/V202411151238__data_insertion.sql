INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY',  10, 31.465, '2024-11-15');
