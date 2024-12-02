INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL',  24.806276, 31.68, '2024-12-02');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL',  8, 31.685, '2024-12-02');
