-- Fix SPPW transaction price from 2025-12-23
-- Correct amount: €7,153.23 for 175.638397132 shares = €40.7270284676 per share

UPDATE portfolio_transaction
SET price = 40.7270284676
WHERE id = (
    SELECT pt.id
    FROM portfolio_transaction pt
    JOIN instrument i ON i.id = pt.instrument_id
    WHERE i.symbol = 'SPPW:GER:EUR'
      AND pt.transaction_type = 'BUY'
      AND pt.transaction_date = '2025-12-23'
      AND pt.quantity = 175.638397132
);
