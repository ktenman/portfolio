INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform,
                                   commission, currency)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 1, 14.25, '2025-11-14', 'LHV', 0,
        'EUR');
