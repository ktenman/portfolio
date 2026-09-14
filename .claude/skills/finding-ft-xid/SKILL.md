---
name: finding-ft-xid
description: Use when adding an instrument that needs FT historical prices, or when the user asks for "the ft.com value/id/xid" for a ticker — finds and verifies the numeric FT xid for the `TICKERS` map in `HistoricalPricesService.kt`.
---

# Finding an FT xid

FT's historical-price endpoint is keyed by a numeric xid, not the symbol. Every Lightyear/FT instrument whose symbol FT can't resolve directly needs a `"SYMBOL" to "xid"` line in `TICKERS` (`src/main/kotlin/ee/tenman/portfolio/ft/HistoricalPricesService.kt`).

FT blocks the default curl UA — always pass `-A "Mozilla/5.0"` (and `dangerouslyDisableSandbox: true` for network).

## Recipe

1. **Search** — lists every listing with its xid:
   ```bash
   curl -s -A "Mozilla/5.0" "https://markets.ft.com/data/searchapi/searchsecurities?query=VGLA" \
     | python3 -c "import json,sys;[print(s['symbol'],s['xid']) for s in json.load(sys.stdin)['data']['security']]"
   ```
   Pick the listing matching our symbol exactly (`VGLA:GER:EUR` = Xetra). Results cap at 5 — if it's missing, query the exchange-qualified form (`VXUS:GER`). Beware lookalikes: `VGLA:DUS`, `:MUN`, `:STU`, `:FRA:EUR` are different, thinner listings.
2. **Cross-check on the tearsheet** — the page embeds its xid:
   ```bash
   curl -s -A "Mozilla/5.0" "https://markets.ft.com/data/etfs/tearsheet/summary?s=VGLA:GER:EUR" \
     | grep -o 'xid&quot;:&quot;[0-9]*' | head -1
   ```
   Use `/data/equities/tearsheet/...` for stocks.
3. **Verify prices** — the same endpoint the app calls:
   ```bash
   curl -s -A "Mozilla/5.0" "https://markets.ft.com/data/equities/ajax/get-historical-prices?startDate=2026/09/01&endDate=2026/09/12&symbol=<xid>" \
     | python3 -c "import json,sys,re;print(re.findall(r'<td>([0-9.,]+)</td>',json.load(sys.stdin)['html'])[:8])"
   ```
   Expect OHLC rows at a plausible price. Empty → wrong xid. Open = high = low = close every day → an illiquid listing; prefer the main exchange.
4. **Add** `"SYMBOL" to "xid",` at the end of `TICKERS`. Non-EUR listings: symbol ends in the currency (`GOOGL:NSQ:USD`) so conversion kicks in.
