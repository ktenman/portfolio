---
name: recording-broker-transactions
description: Use when the user sends broker-app screenshots (Lightyear, LHV, Trading212, Swedbank) of buys/sells and asks to record, log, or migrate them into the portfolio project — "create a migration for the positions I sold yesterday", "add these buys".
---

# Recording broker transactions

Transcribe broker-app screenshots into one Flyway data migration in `~/dev/portfolio`. Mechanical job — the only judgement calls are name→symbol mapping and platform.

## Recipe

1. **Read the screenshots.** Per row: instrument name, quantity (full precision), total amount. The rounded unit price the app shows is a cross-check, never the value you write.
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
5. **Validate before claiming done** — dry-run against dev Postgres, rolled back:
   ```bash
   { echo "BEGIN;"; cat src/main/resources/db/migration/V<version>__*.sql; cat <<'SQL'
   SELECT i.symbol, ROUND(t.quantity * t.price, 2) AS proceeds FROM portfolio_transaction t
     JOIN instrument i ON i.id = t.instrument_id WHERE t.transaction_date = '<date>' ORDER BY proceeds;
   SELECT i.symbol, SUM(CASE WHEN t.transaction_type='BUY' THEN t.quantity ELSE -t.quantity END) AS held
     FROM portfolio_transaction t JOIN instrument i ON i.id=t.instrument_id
     WHERE t.platform='<PLATFORM>' AND i.symbol <> 'EUR' GROUP BY i.symbol HAVING SUM(...) < 0;
   ROLLBACK;
   SQL
   } | docker exec -i portfolio-postgres-dev-1 psql -U postgres -d portfolio -v ON_ERROR_STOP=1
   ```
   Green = every symbol resolved (11 rows in, 11 out), every `proceeds` matches the screenshot to the cent, no position went negative. Flyway applies it for real on app start — never commit the insert manually.
6. **Branch, commit, PR.** `git checkout -b <platform>-<buys|sells>-<mon><dd>`, subject like `Add Lightyear sell transactions from Aug 17`. `git push` needs `dangerouslyDisableSandbox: true`.

## Rules

| Rule                                                       | Why                                                                                 |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `price` = `amount / quantity` as a literal SQL expression  | Fractional shares; the app's rounded unit price loses cents                         |
| Never set `realized_profit` / `average_cost`               | `TransactionQueryService` derives them from buy history                             |
| `commission` = 0                                           | Lightyear/LHV ETF trades are commission-free                                        |
| Omit `EUR` cash rows unless asked                          | ETF trades don't move the cash ledger; only explicit `EUR` rows do                  |
| Reconciling to a stated cash balance? Record the **delta** | Current = `SUM(BUY−SELL)` of `EUR` on that platform — query it first, don't `BUY N` |

## Sanity checks worth running

- Total proceeds landing on a round number (€2,503.00) usually means a targeted withdrawal — good sign the transcription is complete.
- Sold quantities all ≈ the same % of holdings → proportional trim; wildly uneven → suspect a misread row.
- Screenshot rows below the day's group (`Earlier in Aug…`) are NOT part of this trade — but they're the best platform fingerprint.

## Common mistakes

- Writing the rounded unit price instead of `amount / quantity`.
- Version timestamp = trade date. Crash-loops prod.
- Guessing a symbol from a truncated ETF name.
- Claiming done without the BEGIN/ROLLBACK dry-run.
