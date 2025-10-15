INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform,
                                   commission, currency)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 67, 151.26, '2025-10-15', 'SWEDBANK', 14.19,
        'EUR');
