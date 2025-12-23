#!/bin/bash

# ETF Holdings Statistics Script
# Shows breakdown of holdings by source (Lightyear API vs LLM classification)

CONTAINER="portfolio-postgres-dev-1"
DB="portfolio"
USER="postgres"

echo "═══════════════════════════════════════════════════════════════"
echo "                    ETF Holdings Statistics"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Total holdings count
echo "📊 TOTAL HOLDINGS"
echo "─────────────────────────────────────────────────────────────────"
docker exec $CONTAINER psql -U $USER -d $DB -t -c "
SELECT COUNT(*) as total FROM etf_holding;
" | xargs echo "Total ETF holdings:"
echo ""

# Holdings by sector source
echo "📡 HOLDINGS BY SOURCE"
echo "─────────────────────────────────────────────────────────────────"
docker exec $CONTAINER psql -U $USER -d $DB -c "
SELECT
    COALESCE(sector_source, 'UNKNOWN') as source,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM etf_holding
GROUP BY sector_source
ORDER BY count DESC;
"
echo ""

# Holdings classified by LLM models
echo "🤖 LLM CLASSIFICATION BREAKDOWN"
echo "─────────────────────────────────────────────────────────────────"
docker exec $CONTAINER psql -U $USER -d $DB -c "
SELECT
    COALESCE(classified_by_model, 'Not classified by LLM') as model,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM etf_holding
GROUP BY classified_by_model
ORDER BY count DESC;
"
echo ""

# Holdings with sector vs without
echo "🏷️  SECTOR COVERAGE"
echo "─────────────────────────────────────────────────────────────────"
docker exec $CONTAINER psql -U $USER -d $DB -c "
SELECT
    CASE WHEN sector IS NOT NULL THEN 'Has sector' ELSE 'No sector' END as status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM etf_holding
GROUP BY (sector IS NOT NULL)
ORDER BY count DESC;
"
echo ""

# Top sectors
echo "🏭 TOP 10 SECTORS"
echo "─────────────────────────────────────────────────────────────────"
docker exec $CONTAINER psql -U $USER -d $DB -c "
SELECT
    COALESCE(sector, 'Unclassified') as sector,
    COUNT(*) as count
FROM etf_holding
GROUP BY sector
ORDER BY count DESC
LIMIT 10;
"
echo ""

# Summary
echo "═══════════════════════════════════════════════════════════════"
echo "                         SUMMARY"
echo "═══════════════════════════════════════════════════════════════"
docker exec $CONTAINER psql -U $USER -d $DB -t -c "
SELECT
    'Lightyear API' as source,
    COUNT(*) FILTER (WHERE sector_source = 'LIGHTYEAR') as count
FROM etf_holding
UNION ALL
SELECT
    'LLM Classified' as source,
    COUNT(*) FILTER (WHERE sector_source = 'LLM') as count
FROM etf_holding
UNION ALL
SELECT
    'Other/Unknown' as source,
    COUNT(*) FILTER (WHERE sector_source IS NULL OR sector_source NOT IN ('LIGHTYEAR', 'LLM')) as count
FROM etf_holding;
"
