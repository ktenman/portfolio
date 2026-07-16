INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission, remaining_quantity)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'GOOGL:NSQ:USD'), 'BUY', 0.044983773, 354.35 / 1.143, '2026-07-10', 'LIGHTYEAR_BUSINESS', 0, 0.044983773);
