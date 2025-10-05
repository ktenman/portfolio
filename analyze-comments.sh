#!/bin/bash

OUTPUT_FILE="comment-analysis.txt"
TEMP_FILE="temp-comments.txt"

> "$OUTPUT_FILE"
> "$TEMP_FILE"

echo "════════════════════════════════════════════════════════════════"
echo "  Comment Analysis Tool - Clean Code Review"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "Analyzing .kt, .ts, and .vue files for comments..."
echo ""

analyze_file() {
    local file=$1
    local extension=$2

    case $extension in
        kt)
            grep -n "^\s*//\|^\s*/\*\|^\s*\*" "$file" | while IFS=: read -r line_num content; do
                if ! echo "$content" | grep -qE "^\s*//(/|@)|^\s*\* @"; then
                    echo "$file:$line_num:$content" >> "$TEMP_FILE"
                fi
            done
            ;;
        ts)
            grep -n "^\s*//\|^\s*/\*\|^\s*\*" "$file" | while IFS=: read -r line_num content; do
                if ! echo "$content" | grep -qE "^\s*/// <reference|^\s*//.*eslint|^\s*//.*prettier|^\s*//.*@ts-"; then
                    echo "$file:$line_num:$content" >> "$TEMP_FILE"
                fi
            done
            ;;
        vue)
            sed -n '/<script/,/<\/script>/p' "$file" | grep -n "^\s*//\|^\s*/\*\|^\s*\*" | while IFS=: read -r line_num content; do
                if ! echo "$content" | grep -qE "^\s*//.*eslint|^\s*//.*prettier|^\s*/// <reference"; then
                    echo "$file:$line_num:$content" >> "$TEMP_FILE"
                fi
            done
            ;;
    esac
}

echo "Analyzing Kotlin files (.kt)..."
kt_count=0
while IFS= read -r file; do
    analyze_file "$file" "kt"
    ((kt_count++))
done < <(find src -name "*.kt" -type f)

echo "Analyzing TypeScript files (.ts)..."
ts_count=0
while IFS= read -r file; do
    analyze_file "$file" "ts"
    ((ts_count++))
done < <(find ui -name "*.ts" -type f 2>/dev/null)

echo "Analyzing Vue files (.vue)..."
vue_count=0
while IFS= read -r file; do
    analyze_file "$file" "vue"
    ((vue_count++))
done < <(find ui -name "*.vue" -type f 2>/dev/null)

echo ""
echo "Files analyzed: $kt_count Kotlin, $ts_count TypeScript, $vue_count Vue"
echo ""

{
    echo "════════════════════════════════════════════════════════════════"
    echo "  COMMENT ANALYSIS REPORT"
    echo "  Generated: $(date)"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    echo "Files analyzed: $kt_count Kotlin, $ts_count TypeScript, $vue_count Vue"
    echo ""
    echo "LEGEND:"
    echo "  [KEEP]    - Required/useful comments (copyright, suppression, directives)"
    echo "  [REVIEW]  - Potentially redundant comments to review"
    echo "  [REMOVE]  - Clear candidates for removal (violate clean code)"
    echo ""
    echo "════════════════════════════════════════════════════════════════"
    echo ""
} >> "$OUTPUT_FILE"

if [ -s "$TEMP_FILE" ]; then
    echo "Categorizing comments..."
    echo ""

    {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "KOTLIN FILES (.kt)"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    grep "\.kt:" "$TEMP_FILE" | while IFS= read -r line; do
        file=$(echo "$line" | cut -d: -f1)
        line_num=$(echo "$line" | cut -d: -f2)
        comment=$(echo "$line" | cut -d: -f3-)

        category="[REVIEW]"

        if echo "$comment" | grep -qE "TODO|FIXME|XXX|HACK|NOTE"; then
            category="[KEEP]  "
        elif echo "$comment" | grep -qE "Copyright|License|Author|@"; then
            category="[KEEP]  "
        elif echo "$comment" | grep -qE "First,|Second,|Step [0-9]|Now|Then|Finally|Check if|Calculate|Update|Set|Get|Return|Validate"; then
            category="[REMOVE]"
        elif echo "$comment" | grep -qE "^\s*/\*\*|^\s*\*\s*@"; then
            category="[REVIEW]"
        fi

        echo "$category $file:$line_num" >> "$OUTPUT_FILE"
        echo "         $comment" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    done

    {
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "TYPESCRIPT FILES (.ts)"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    grep "\.ts:" "$TEMP_FILE" | while IFS= read -r line; do
        file=$(echo "$line" | cut -d: -f1)
        line_num=$(echo "$line" | cut -d: -f2)
        comment=$(echo "$line" | cut -d: -f3-)

        category="[REVIEW]"

        if echo "$comment" | grep -qE "TODO|FIXME|XXX|HACK|NOTE"; then
            category="[KEEP]  "
        elif echo "$comment" | grep -qE "Copyright|License|Author|@ts-|@type"; then
            category="[KEEP]  "
        elif echo "$comment" | grep -qE "First,|Second,|Step [0-9]|Now|Then|Finally|Check if|Calculate|Update|Set|Get|Return|Validate"; then
            category="[REMOVE]"
        fi

        echo "$category $file:$line_num" >> "$OUTPUT_FILE"
        echo "         $comment" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    done

    {
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "VUE FILES (.vue)"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    grep "\.vue:" "$TEMP_FILE" | while IFS= read -r line; do
        file=$(echo "$line" | cut -d: -f1)
        line_num=$(echo "$line" | cut -d: -f2)
        comment=$(echo "$line" | cut -d: -f3-)

        category="[REVIEW]"

        if echo "$comment" | grep -qE "TODO|FIXME|XXX|HACK|NOTE"; then
            category="[KEEP]  "
        elif echo "$comment" | grep -qE "Copyright|License|Author"; then
            category="[KEEP]  "
        elif echo "$comment" | grep -qE "First,|Second,|Step [0-9]|Now|Then|Finally|Check if|Calculate|Update|Set|Get|Return|Validate"; then
            category="[REMOVE]"
        fi

        echo "$category $file:$line_num" >> "$OUTPUT_FILE"
        echo "         $comment" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    done

    {
        echo ""
        echo "════════════════════════════════════════════════════════════════"
        echo "SUMMARY"
        echo "════════════════════════════════════════════════════════════════"
        echo ""
        echo "Total comments found: $(wc -l < "$TEMP_FILE")"
        echo "  [KEEP]   comments: $(grep -c "\[KEEP\]" "$OUTPUT_FILE" || echo 0)"
        echo "  [REVIEW] comments: $(grep -c "\[REVIEW\]" "$OUTPUT_FILE" || echo 0)"
        echo "  [REMOVE] comments: $(grep -c "\[REMOVE\]" "$OUTPUT_FILE" || echo 0)"
        echo ""
        echo "Review the report in: $OUTPUT_FILE"
        echo ""
        echo "Next steps:"
        echo "  1. Review [REMOVE] comments - these clearly violate clean code"
        echo "  2. Review [REVIEW] comments - evaluate if they add value"
        echo "  3. Keep [KEEP] comments - these are necessary"
        echo ""
    } >> "$OUTPUT_FILE"

else
    echo "No comments found!" >> "$OUTPUT_FILE"
fi

rm -f "$TEMP_FILE"

echo "════════════════════════════════════════════════════════════════"
echo "Analysis complete! Report saved to: $OUTPUT_FILE"
echo "════════════════════════════════════════════════════════════════"
echo ""

cat "$OUTPUT_FILE" | grep -A 5 "SUMMARY"
