-- Increase decimal precision from 10 to 12 for financial calculations
-- This provides higher precision for intermediate calculations and matches industry standards

-- portfolio_transaction table
ALTER TABLE portfolio_transaction
    ALTER COLUMN quantity TYPE NUMERIC(22, 12),
    ALTER COLUMN price TYPE NUMERIC(22, 12),
    ALTER COLUMN commission TYPE NUMERIC(22, 12),
    ALTER COLUMN realized_profit TYPE NUMERIC(22, 12),
    ALTER COLUMN unrealized_profit TYPE NUMERIC(22, 12),
    ALTER COLUMN average_cost TYPE NUMERIC(22, 12),
    ALTER COLUMN remaining_quantity TYPE NUMERIC(22, 12);

-- instrument table
ALTER TABLE instrument
    ALTER COLUMN current_price TYPE NUMERIC(22, 12);

-- portfolio_daily_summary table
ALTER TABLE portfolio_daily_summary
    ALTER COLUMN total_value TYPE NUMERIC(22, 12),
    ALTER COLUMN xirr_annual_return TYPE NUMERIC(22, 12),
    ALTER COLUMN total_profit TYPE NUMERIC(22, 12),
    ALTER COLUMN earnings_per_day TYPE NUMERIC(22, 12);
