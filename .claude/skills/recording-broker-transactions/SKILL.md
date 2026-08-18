---
name: recording-broker-transactions
description: Use when the user sends broker-app screenshots (Lightyear, LHV, Trading212, Swedbank) of buys/sells and asks to record, log, or migrate them into the portfolio project — "create a migration for the positions I sold yesterday", "add these buys".
---

# Recording broker transactions

Transcribe broker-app screenshots into one Flyway data migration. Mechanical job — the only judgement calls are name→symbol mapping and platform.

## Recipe

1. **Read the screenshots.** Per row: instrument name, quantity (full precision), total amount, and the rounded unit price. Rows below the day's group (`Earlier in Aug…`) are not part of this trade — but they are the best platform fingerprint.
2. **Map names to symbols** — query the dev DB, never guess:
   ```bash
   docker exec portfolio-postgres-dev-1 psql -U postgres -d portfolio -c \
     "SELECT symbol, name FROM instrument ORDER BY symbol;"
   ```
   Symbol format `TICKER:EXCHANGE:CUR` (`AIFS:GER:EUR`); cash is `EUR`. Names truncate in the app — `iShares MSCI Global Semico…` is `SEC0`, not the obvious guess. No match → a separate `add_<ticker>_instrument` migration must land first (needs `provider_external_id`, the Lightyear UUID) — ask.
3. **Pick the platform.** `LIGHTYEAR`, `LIGHTYEAR_BUSINESS`, `LHV`, `TRADING212`, `SWEDBANK`, `BINANCE`, `AVIVA`. Personal vs business Lightyear is invisible in a screenshot: disambiguate with an unrelated row in the same list (an older trade whose amount matches exactly one existing migration), or ask.
4. **Write the migration**, `src/main/resources/db/migration/V<YYYYMMDDHHMM>__<platform>_<buys|sells>_<mon><dd>.sql`:
   ```sql
   INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
   VALUES
       ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.52701218, 110.33 / 0.52701218, '2026-08-17', 'LIGHTYEAR', 0);
   ```
   Version = `date "+%Y%m%d%H%M"` (now), NOT the trade date — Flyway out-of-order is off, so a backdated version crash-loops prod on deploy. The real date goes in `transaction_date`.
5. **Dry-run before claiming done** — apply and roll back against dev Postgres:
   ```bash
   { echo 'BEGIN;'
     cat src/main/resources/db/migration/V<version>__*.sql
     echo "SELECT i.symbol, ROUND(t.price, 2) AS unit, ROUND(t.quantity * t.price, 2) AS proceeds
             FROM portfolio_transaction t JOIN instrument i ON i.id = t.instrument_id
             WHERE t.transaction_date = '<date>' ORDER BY proceeds;
           SELECT * FROM (
             SELECT i.symbol, SUM(CASE WHEN t.transaction_type = 'BUY' THEN t.quantity ELSE -t.quantity END) AS held
               FROM portfolio_transaction t JOIN instrument i ON i.id = t.instrument_id
               WHERE t.platform = '<PLATFORM>' AND i.symbol <> 'EUR' GROUP BY i.symbol) h
           WHERE held < 0;"
     echo 'ROLLBACK;'
   } | docker exec -i portfolio-postgres-dev-1 psql -U postgres -d portfolio -v ON_ERROR_STOP=1
   ```
   Green = every `proceeds` matches the screenshot amount **and** every `unit` matches its shown unit price, and the second query returns no rows. Each row appears twice once the dev backend has already applied the file — expected, not a duplicate insert. Both halves matter: `price` is derived from `quantity`, so `proceeds` reconstructs even from a misread quantity — only `unit` catches that. Flyway applies the file for real on app start; never commit the insert manually.
6. **Branch, commit, PR** per the git conventions in AGENTS.md. No issue exists for these — sibling branches are named `feature/lightyear-sells-jun29`.

## Rules

| Rule                                                      | Why                                                                                                  |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `price` = `amount / quantity` as a literal SQL expression | Fractional shares; the app's rounded unit price loses cents                                          |
| Never set `realized_profit` / `average_cost`              | `TransactionQueryService` derives them from buy history                                              |
| `commission` = 0                                          | Lightyear/LHV ETF trades are commission-free                                                         |
| Omit `EUR` cash rows unless asked                         | ETF trades don't move the cash ledger; reconciling to a stated balance means recording the **delta** against `SUM(BUY−SELL)` of `EUR` on that platform — query it first |
