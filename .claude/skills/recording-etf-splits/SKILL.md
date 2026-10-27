---
name: recording-etf-splits
description: Use when an instrument's price drops or jumps by a near-whole-number ratio overnight (355.07 → 4.44) while nothing else moves, a position's value collapses, or the user suspects a broken price provider and wants to switch providers.
---

# Recording ETF splits

The app has no split support: a holding is its summed transaction quantity times the provider's price. After a split the provider's quote is right and our share count is wrong, so switching providers never helps — every source quotes the post-split price. Fix it with one Flyway migration that rewrites the history in post-split units (#2297, BNKE 1:80).

## Recipe

Read prod with SELECTs only: `ssh root@fov.ee "docker exec postgres psql -U fov.ee -d portfolio -c '…'"`.

1. **Confirm the split.** Last pre-split ÷ first post-split `price_snapshot` is a near-whole ratio n, and no other instrument moved. Get the exact ratio and ex-date from the issuer's shareholder notice.
2. **Check the provider's history job** (table below). If it will rewrite old prices unadjusted, stop and ask.
3. **Find each cut-off in the data, not the calendar.** The split morning's early snapshot hours still hold the old price.
   - `daily_price`: `entry_date` before the ex-date
   - `price_snapshot`: the first `snapshot_hour` holding the new price
   - `instrument_minute_price`: the first `captured_at` holding the new price
   - `portfolio_intraday_summary`: the first `captured_at` whose total dropped, for `platform_key` `''` and every platform holding the instrument
4. **Write `V<date "+%Y%m%d%H%M">__<ticker>_split_1_to_<n>.sql`**, shaped like #2297:
   ```sql
   UPDATE portfolio_transaction SET quantity = quantity * 80, price = price / 80
   WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR') AND transaction_date < '2026-10-27';

   UPDATE daily_price SET open_price = open_price / 80, high_price = high_price / 80, low_price = low_price / 80, close_price = close_price / 80
   WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR') AND entry_date < '2026-10-27';

   UPDATE price_snapshot SET price = price / 80
   WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR') AND snapshot_hour < '2026-10-27 07:00:00+00';

   UPDATE instrument_minute_price SET price = price / 80
   WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR') AND captured_at < '2026-10-27 07:00:00+00';

   DELETE FROM portfolio_intraday_summary
   WHERE platform_key IN ('', 'LHV') AND captured_at >= '2026-10-27 07:17:00+00';
   ```
5. **Dry-run on dev** inside `BEGIN … ROLLBACK` (`docker exec -i portfolio-postgres-dev-1 psql -U postgres -d portfolio -v ON_ERROR_STOP=1`). Green = the held quantity is n times larger, `SUM(quantity * price)` is unchanged, and no row is left on the wrong side of a cut-off. Check the same cut-offs on prod with SELECTs.
6. **Ship through a PR** on `fix/<ticker>-split-1-to-<n>`. After the deploy, the share count matches the broker app.

## Provider history

| `provider_name` | Writer of old `daily_price` rows                                                 | Effect on the migration                       |
| --------------- | -------------------------------------------------------------------------------- | --------------------------------------------- |
| `TRADING212`    | `InstrumentPriceGapFillingJob` inserts FT history for missing dates only         | Adjusted rows stay                            |
| `FT`            | `FtDataRetrievalJob` re-upserts FT's whole history every night                   | Undone unless FT's history is split-adjusted  |
| `LIGHTYEAR`     | `LightyearHistoricalDataRetrievalJob` re-upserts Lightyear's history every night | Undone unless Lightyear's is split-adjusted   |

Check FT with `get-historical-prices` for a pre-split date (finding-ft-xid skill). On BNKE's split day FT still served the old closes unadjusted.

## Rules

| Rule                                                        | Why                                                                                 |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Cut off by date or time, never by a price threshold         | Years of history cross any threshold: BNKE closed at 46 in 2020, 406 in 2026 |
| Delete the wrong intraday rows, don't rescale them          | They are stored totals across every instrument                                      |
| Leave `portfolio_daily_summary` alone                       | Old rows are split-invariant and the nightly job recomputes every row               |
| Never also run the SQL by hand on prod                      | Flyway applies it again: the ratio squared                                          |
| Version = now                                               | A backdated version crash-loops prod on deploy                                      |
| Skip `realized_profit`, `average_cost`, `remaining_quantity` | Every reader recalculates them                                                      |
