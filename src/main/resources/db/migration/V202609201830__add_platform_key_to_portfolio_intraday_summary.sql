ALTER TABLE portfolio_intraday_summary
    ADD COLUMN platform_key VARCHAR(50) NOT NULL DEFAULT '';

ALTER TABLE portfolio_intraday_summary
    DROP CONSTRAINT portfolio_intraday_summary_captured_at_key;

ALTER TABLE portfolio_intraday_summary
    ADD CONSTRAINT uk_intraday_summary_capture UNIQUE (captured_at, platform_key);
