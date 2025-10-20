ALTER TABLE portfolio_daily_summary
ADD COLUMN realized_profit NUMERIC(19, 2) NOT NULL DEFAULT 0,
ADD COLUMN unrealized_profit NUMERIC(19, 2) NOT NULL DEFAULT 0;
