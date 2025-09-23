ALTER TABLE portfolio_transaction
ADD COLUMN remaining_quantity NUMERIC(19, 8);

UPDATE portfolio_transaction
SET remaining_quantity = quantity
WHERE transaction_type = 'BUY';

UPDATE portfolio_transaction
SET remaining_quantity = 0
WHERE transaction_type = 'SELL';