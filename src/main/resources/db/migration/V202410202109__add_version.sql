ALTER TABLE user_account ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE portfolio_transaction ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE portfolio_daily_summary ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE job_execution ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE instrument ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE daily_price ADD COLUMN version BIGINT DEFAULT 0;
