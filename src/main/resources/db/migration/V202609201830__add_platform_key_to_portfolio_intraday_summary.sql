ALTER TABLE portfolio_intraday_summary
    ADD COLUMN platform_key VARCHAR(50) NOT NULL DEFAULT '',
    DROP CONSTRAINT portfolio_intraday_summary_captured_at_key,
    ADD CONSTRAINT uk_intraday_summary_capture UNIQUE (platform_key, captured_at);

ALTER TABLE portfolio_intraday_summary
    ALTER COLUMN platform_key DROP DEFAULT;
