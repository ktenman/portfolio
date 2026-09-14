UPDATE portfolio_daily_summary s
SET earnings_per_day =
  (s.total_profit - COALESCE((SELECT b.total_profit
                              FROM portfolio_daily_summary b
                              WHERE b.entry_date = s.entry_date - 365), 0))
  / GREATEST(1, LEAST(365, s.entry_date - (SELECT MIN(t.transaction_date) FROM portfolio_transaction t)));
