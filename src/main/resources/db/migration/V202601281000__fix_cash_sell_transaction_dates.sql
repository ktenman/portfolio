-- Fix SELL transaction dates to match when cash was actually used for ETF purchases
-- Cash was deposited on 13.01 and 16.01, but used to buy ETFs on 22.01

UPDATE portfolio_transaction
SET transaction_date = '2026-01-22'
WHERE id IN (191, 192, 193)
  AND transaction_type = 'SELL';
