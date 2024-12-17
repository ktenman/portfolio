ALTER TABLE portfolio_transaction
  ADD COLUMN IF NOT EXISTS realized_profit NUMERIC (20, 10),
  ADD COLUMN IF NOT EXISTS unrealized_profit NUMERIC (20, 10),
  ADD COLUMN IF NOT EXISTS average_cost NUMERIC (20, 10);

ALTER TABLE portfolio_transaction
  ALTER COLUMN realized_profit SET DEFAULT 0,
ALTER
COLUMN unrealized_profit SET DEFAULT 0,
  ALTER
COLUMN average_cost SET DEFAULT 0;

UPDATE portfolio_transaction
SET realized_profit = 0
WHERE realized_profit IS NULL;

UPDATE portfolio_transaction
SET unrealized_profit = 0
WHERE unrealized_profit IS NULL;

UPDATE portfolio_transaction
SET average_cost = price
WHERE average_cost IS NULL;
