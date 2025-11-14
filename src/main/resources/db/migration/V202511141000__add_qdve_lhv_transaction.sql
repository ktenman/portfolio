INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform,
                                   commission, currency)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 16, 35.725, '2025-11-14', 'LHV', 0,
        'EUR');
