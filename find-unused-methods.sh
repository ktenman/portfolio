#!/bin/bash

OUTPUT_FILE="unused-methods-report.txt"

> "$OUTPUT_FILE"

echo "════════════════════════════════════════════════════════════════"
echo "  Comprehensive Unused Method Detection"
echo "════════════════════════════════════════════════════════════════"
echo ""

{
    echo "════════════════════════════════════════════════════════════════"
    echo "  UNUSED METHOD ANALYSIS REPORT"
    echo "  Generated: $(date)"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
} >> "$OUTPUT_FILE"

check_method_usage() {
    local method_name=$1
    local file=$2
    local search_path=$3

    # Count total occurrences across all Kotlin files
    local total_count=$(grep -r "\b$method_name\b" "$search_path" --include="*.kt" 2>/dev/null | wc -l)

    # Count definition occurrences (fun methodName)
    local def_count=$(grep -r "fun\s\+$method_name\s*(" "$search_path" --include="*.kt" 2>/dev/null | wc -l)

    # If total count equals definition count, it's only defined but never called
    if [ "$total_count" -eq "$def_count" ]; then
        return 0  # Unused
    else
        return 1  # Used
    fi
}

analyze_repositories() {
    echo "Analyzing Repository files..."
    local count=0
    local unused=0

    {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "REPOSITORY INTERFACES"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    while IFS= read -r file; do
        grep -n "^\s*fun\s\+[a-zA-Z_][a-zA-Z0-9_]*\s*(" "$file" | while IFS=: read -r line_num line_content; do
            method_name=$(echo "$line_content" | sed -E 's/.*fun[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]*).*/\1/')

            # Skip common JPA methods
            if echo "$method_name" | grep -qE "^(save|findById|findAll|deleteById|delete|count|existsById)$"; then
                continue
            fi

            ((count++))

            if check_method_usage "$method_name" "$file" "src"; then
                ((unused++))
                echo "[UNUSED] $file:$line_num" >> "$OUTPUT_FILE"
                echo "         Method: $method_name()" >> "$OUTPUT_FILE"
                echo "         $(echo "$line_content" | sed 's/^[[:space:]]*/         /')" >> "$OUTPUT_FILE"
                echo "" >> "$OUTPUT_FILE"
            fi
        done
    done < <(find src/main/kotlin -name "*Repository.kt" -type f)

    {
        echo "Repository Summary: $count methods checked, $unused unused"
        echo ""
    } >> "$OUTPUT_FILE"

    echo "  Repositories: $count methods, $unused unused"
}

analyze_services() {
    echo "Analyzing Service classes..."
    local count=0
    local unused=0

    {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "SERVICE CLASSES"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    while IFS= read -r file; do
        # Find all public/internal/protected methods (not private)
        grep -n "^\s*\(internal\|protected\|public\|\)\s*fun\s\+[a-zA-Z_][a-zA-Z0-9_]*\s*(" "$file" | \
        grep -v "private" | while IFS=: read -r line_num line_content; do
            method_name=$(echo "$line_content" | sed -E 's/.*fun[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]*).*/\1/')

            # Skip test methods, overrides, and common lifecycle methods
            if echo "$method_name" | grep -qE "^(test|should|toString|hashCode|equals|execute)$"; then
                continue
            fi

            # Skip if it's an override
            if echo "$line_content" | grep -q "override"; then
                continue
            fi

            # Check if method has @Scheduled, @PostConstruct, etc.
            local line_before=$((line_num - 1))
            local annotation=$(sed -n "${line_before}p" "$file")
            if echo "$annotation" | grep -qE "@(Scheduled|PostConstruct|PreDestroy|Bean|EventListener)"; then
                continue
            fi

            ((count++))

            if check_method_usage "$method_name" "$file" "src"; then
                ((unused++))
                echo "[UNUSED] $file:$line_num" >> "$OUTPUT_FILE"
                echo "         Method: $method_name()" >> "$OUTPUT_FILE"
                echo "         $(echo "$line_content" | sed 's/^[[:space:]]*/         /')" >> "$OUTPUT_FILE"
                echo "" >> "$OUTPUT_FILE"
            fi
        done
    done < <(find src/main/kotlin -name "*Service.kt" -type f)

    {
        echo "Service Summary: $count methods checked, $unused unused"
        echo ""
    } >> "$OUTPUT_FILE"

    echo "  Services: $count methods, $unused unused"
}

analyze_controllers() {
    echo "Analyzing Controller classes..."
    local count=0
    local unused=0

    {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "CONTROLLER CLASSES"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    while IFS= read -r file; do
        grep -n "^\s*\(internal\|protected\|public\|\)\s*fun\s\+[a-zA-Z_][a-zA-Z0-9_]*\s*(" "$file" | \
        grep -v "private" | while IFS=: read -r line_num line_content; do
            method_name=$(echo "$line_content" | sed -E 's/.*fun[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]*).*/\1/')

            # Controllers are HTTP endpoints, check for mapping annotations
            local line_before=$((line_num - 1))
            local annotation=$(sed -n "${line_before}p" "$file")
            if echo "$annotation" | grep -qE "@(Get|Post|Put|Delete|Patch|Request)Mapping"; then
                continue  # Skip REST endpoints
            fi

            ((count++))

            if check_method_usage "$method_name" "$file" "src"; then
                ((unused++))
                echo "[UNUSED] $file:$line_num" >> "$OUTPUT_FILE"
                echo "         Method: $method_name()" >> "$OUTPUT_FILE"
                echo "         $(echo "$line_content" | sed 's/^[[:space:]]*/         /')" >> "$OUTPUT_FILE"
                echo "" >> "$OUTPUT_FILE"
            fi
        done
    done < <(find src/main/kotlin -name "*Controller.kt" -type f)

    {
        echo "Controller Summary: $count methods checked, $unused unused"
        echo ""
    } >> "$OUTPUT_FILE"

    echo "  Controllers: $count methods, $unused unused"
}

analyze_utils_and_others() {
    echo "Analyzing Utils, Helpers, and other classes..."
    local count=0
    local unused=0

    {
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "UTILS, HELPERS & OTHER CLASSES"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
    } >> "$OUTPUT_FILE"

    while IFS= read -r file; do
        # Skip repositories, services, controllers
        if echo "$file" | grep -qE "(Repository|Service|Controller|Test)\.kt$"; then
            continue
        fi

        # Find public/internal methods
        grep -n "^\s*\(internal\|public\|\)\s*fun\s\+[a-zA-Z_][a-zA-Z0-9_]*\s*(" "$file" | \
        grep -v "private" | while IFS=: read -r line_num line_content; do
            method_name=$(echo "$line_content" | sed -E 's/.*fun[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]*).*/\1/')

            # Skip common methods
            if echo "$method_name" | grep -qE "^(main|toString|hashCode|equals)$"; then
                continue
            fi

            # Skip overrides
            if echo "$line_content" | grep -q "override"; then
                continue
            fi

            ((count++))

            if check_method_usage "$method_name" "$file" "src"; then
                ((unused++))
                echo "[UNUSED] $file:$line_num" >> "$OUTPUT_FILE"
                echo "         Method: $method_name()" >> "$OUTPUT_FILE"
                echo "         $(echo "$line_content" | sed 's/^[[:space:]]*/         /')" >> "$OUTPUT_FILE"
                echo "" >> "$OUTPUT_FILE"
            fi
        done
    done < <(find src/main/kotlin -name "*.kt" -type f)

    {
        echo "Utils/Others Summary: $count methods checked, $unused unused"
        echo ""
    } >> "$OUTPUT_FILE"

    echo "  Utils/Others: $count methods, $unused unused"
}

# Run all analyses
analyze_repositories
analyze_services
analyze_controllers
analyze_utils_and_others

{
    echo "════════════════════════════════════════════════════════════════"
    echo "OVERALL SUMMARY"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    echo "⚠️  IMPORTANT - Possible False Positives:"
    echo ""
    echo "  • Spring framework methods (@Scheduled, @PostConstruct, @EventListener)"
    echo "  • REST endpoints (@GetMapping, @PostMapping, etc.)"
    echo "  • Methods called via reflection or Kotlin delegates"
    echo "  • Interface methods implemented but called through interface"
    echo "  • Methods used in tests (check test folder separately)"
    echo ""
    echo "✅  Review each method carefully before removal!"
    echo ""
    echo "Full report saved to: $OUTPUT_FILE"
    echo ""
} >> "$OUTPUT_FILE"

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "Analysis complete! Full report: $OUTPUT_FILE"
echo "════════════════════════════════════════════════════════════════"
