ALTER TABLE instrument ADD COLUMN ter DECIMAL(6, 4);

COMMENT ON COLUMN instrument.ter IS 'Total Expense Ratio as percentage (e.g., 0.40 for 0.40%)';
