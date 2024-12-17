ALTER TABLE portfolio_transaction
  ADD COLUMN IF NOT EXISTS realized_profit NUMERIC(20, 10),
  ADD COLUMN IF NOT EXISTS unrealized_profit NUMERIC(20, 10),
  ADD COLUMN IF NOT EXISTS average_cost NUMERIC(20, 10);
