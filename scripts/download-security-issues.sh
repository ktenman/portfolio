#!/bin/bash

set -euo pipefail
IFS=$'\n\t'

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly OUTPUT_FILE="${SCRIPT_DIR}/security-issues-report.md"
readonly CSV_FILE="${SCRIPT_DIR}/security-issues.csv"
readonly PRIORITY_FILE="${SCRIPT_DIR}/top-50-fixes.md"
readonly JSON_FILE="${SCRIPT_DIR}/alerts.json"
readonly GITHUB_REPO=${GITHUB_REPO:-"ktenman/portfolio"}

validate_repo_name() {
  local repo="$1"
  if [[ ! "$repo" =~ ^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$ ]]; then
    echo "Error: Invalid repository format. Expected: owner/repo" >&2
    exit 1
  fi
}

check_dependencies() {
  local missing=()

  if ! command -v gh &> /dev/null; then
    missing+=("gh (GitHub CLI)")
  fi

  if ! command -v jq &> /dev/null; then
    missing+=("jq")
  fi

  if [ ${#missing[@]} -gt 0 ]; then
    echo "Error: Missing required dependencies: ${missing[*]}" >&2
    echo "" >&2
    echo "Installation:" >&2
    echo "  macOS: brew install ${missing[*]}" >&2
    echo "  Linux: apt install ${missing[*]} or yum install ${missing[*]}" >&2
    exit 1
  fi
}

validate_repo_name "$GITHUB_REPO"
check_dependencies

echo "Checking GitHub CLI authentication..."

if ! gh auth status &> /dev/null; then
    echo "Error: Not authenticated with GitHub CLI" >&2
    echo "" >&2
    echo "Please run: gh auth login" >&2
    echo "And follow the prompts to authenticate." >&2
    exit 1
fi

echo "✅ GitHub CLI is authenticated"
echo "Fetching code scanning alerts for $GITHUB_REPO..."

if ! ALERTS=$(gh api \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "/repos/${GITHUB_REPO}/code-scanning/alerts?state=open&per_page=100" \
  --paginate 2>&1); then
  echo "Error: Failed to fetch alerts from GitHub API" >&2
  echo "Response: $ALERTS" >&2
  exit 1
fi

if [ -z "$ALERTS" ] || [ "$ALERTS" = "[]" ]; then
    echo "No code scanning alerts found."
    echo ""
    echo "Possible reasons:"
    echo "1. Code scanning might not be enabled for this repository"
    echo "2. There are no open security alerts"
    echo "3. You might not have the required permissions"
    echo ""
    echo "To check manually, visit:"
    echo "https://github.com/ktenman/portfolio/security/code-scanning"
    exit 0
fi

echo "$ALERTS" > "$JSON_FILE"

TOTAL_ALERTS=$(echo "$ALERTS" | jq -s 'add | length')
echo "Found $TOTAL_ALERTS open security alerts"

cat > "$OUTPUT_FILE" << EOF
# Security Issues Report

Generated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')  
Repository: ktenman/portfolio  
Total Open Issues: $TOTAL_ALERTS

## Summary by Severity

EOF

ALL_ALERTS=$(echo "$ALERTS" | jq -s 'add')

echo "$ALL_ALERTS" | jq -r '
    group_by(.rule.severity // "unknown") | 
    map({
        severity: (.[0].rule.severity // "unknown"), 
        count: length
    }) | 
    sort_by(.severity) | 
    reverse | 
    .[] | 
    "- **\(.severity | ascii_upcase)**: \(.count) issues"
' >> "$OUTPUT_FILE"

echo "" >> "$OUTPUT_FILE"
echo "## Summary by Rule" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

echo "$ALL_ALERTS" | jq -r '
    group_by(.rule.id // "unknown") | 
    map({
        rule: (.[0].rule.id // "unknown"),
        name: (.[0].rule.name // "Unknown"),
        severity: (.[0].rule.severity // "unknown"),
        count: length
    }) | 
    sort_by(.count) | 
    reverse | 
    .[] | 
    "- **\(.rule)** (\(.severity)): \(.count) - \(.name)"
' >> "$OUTPUT_FILE"

echo "" >> "$OUTPUT_FILE"
echo "## Detailed Issues" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

for severity in "critical" "high" "medium" "low" "warning" "note"; do
    SEVERITY_ALERTS=$(echo "$ALL_ALERTS" | jq --arg sev "$severity" '[.[] | select((.rule.severity // "unknown") == $sev)]')
    SEVERITY_COUNT=$(echo "$SEVERITY_ALERTS" | jq 'length')
    
    if [ "$SEVERITY_COUNT" -gt 0 ]; then
        echo "### $(echo "$severity" | tr '[:lower:]' '[:upper:]') Severity Issues ($SEVERITY_COUNT)" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
        
        echo "$SEVERITY_ALERTS" | jq -r '
            .[] | 
            "#### Issue #\(.number): \(.rule.name // "Unknown")

**Rule ID**: `\(.rule.id // "unknown")`  
**File**: `\(.most_recent_instance.location.path // "unknown"):\(.most_recent_instance.location.start_line // 0)`  
**Created**: \(.created_at // "unknown")  
**State**: \(.state // "unknown")  

**Description**: \(.rule.description // "No description available")

**Message**: \(.most_recent_instance.message.text // "No message available")

**Location**: 
- Path: `\(.most_recent_instance.location.path // "unknown")`
- Lines: \(.most_recent_instance.location.start_line // 0)-\(.most_recent_instance.location.end_line // 0)

---
"
        ' >> "$OUTPUT_FILE"
    fi
done

echo "Creating CSV summary..."
echo "Number,Severity,Rule,File,Line,Message" > "$CSV_FILE"
echo "$ALL_ALERTS" | jq -r '
    .[] | 
    [
        .number,
        (.rule.severity // "unknown"),
        (.rule.id // "unknown"),
        (.most_recent_instance.location.path // "unknown"),
        (.most_recent_instance.location.start_line // 0),
        ((.most_recent_instance.message.text // "No message") | gsub("[,\n]"; " "))
    ] | 
    @csv
' >> "$CSV_FILE"

cat > "$PRIORITY_FILE" << EOF
# Top 50 Security Issues to Fix

Generated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')

## Priority Issues by Severity

These are the top 50 most critical security issues that should be addressed first, sorted by severity and creation date.

EOF

CRITICAL_HIGH=$(echo "$ALL_ALERTS" | jq '[.[] | select((.rule.severity // "unknown") == "critical" or (.rule.severity // "unknown") == "high")] | length')
MEDIUM_COUNT=$(echo "$ALL_ALERTS" | jq '[.[] | select((.rule.severity // "unknown") == "medium")] | length')

if [ "$CRITICAL_HIGH" -gt 0 ]; then
    echo "### Critical and High Severity Issues" >> "$PRIORITY_FILE"
    echo "" >> "$PRIORITY_FILE"
    
    echo "$ALL_ALERTS" | jq -r '
        [.[] | select((.rule.severity // "unknown") == "critical" or (.rule.severity // "unknown") == "high")] | 
        sort_by(.created_at // "9999") | 
        .[0:50] | 
        to_entries | 
        .[] | 
        "### \(.key + 1). \(.value.rule.name // "Unknown") (\(.value.rule.severity // "unknown" | ascii_upcase))
- **Issue #**: \(.value.number)
- **File**: `\(.value.most_recent_instance.location.path // "unknown"):\(.value.most_recent_instance.location.start_line // 0)`
- **Rule**: `\(.value.rule.id // "unknown")`
- **Issue**: \(.value.most_recent_instance.message.text // "No message available")
- **Created**: \(.value.created_at // "unknown")
"
    ' >> "$PRIORITY_FILE"
fi

SHOWN_COUNT=$CRITICAL_HIGH
if [ "$SHOWN_COUNT" -lt 50 ] && [ "$MEDIUM_COUNT" -gt 0 ]; then
    REMAINING=$((50 - SHOWN_COUNT))
    
    if [ "$CRITICAL_HIGH" -eq 0 ]; then
        echo "### Medium Severity Issues" >> "$PRIORITY_FILE"
    else
        echo "" >> "$PRIORITY_FILE"
        echo "### Additional Medium Severity Issues" >> "$PRIORITY_FILE"
    fi
    echo "" >> "$PRIORITY_FILE"
    
    echo "$ALL_ALERTS" | jq -r --argjson start "$SHOWN_COUNT" --argjson limit "$REMAINING" '
        [.[] | select((.rule.severity // "unknown") == "medium")] | 
        sort_by(.created_at // "9999") | 
        .[0:$limit] | 
        to_entries | 
        .[] | 
        "### \($start + .key + 1). \(.value.rule.name // "Unknown") (MEDIUM)
- **Issue #**: \(.value.number)
- **File**: `\(.value.most_recent_instance.location.path // "unknown"):\(.value.most_recent_instance.location.start_line // 0)`
- **Rule**: `\(.value.rule.id // "unknown")`
- **Issue**: \(.value.most_recent_instance.message.text // "No message available")
- **Created**: \(.value.created_at // "unknown")
"
    ' >> "$PRIORITY_FILE"
    
    SHOWN_COUNT=$((SHOWN_COUNT + MEDIUM_COUNT))
    if [ "$SHOWN_COUNT" -gt 50 ]; then
        SHOWN_COUNT=50
    fi
fi

LOW_COUNT=$(echo "$ALL_ALERTS" | jq '[.[] | select((.rule.severity // "unknown") == "low")] | length')
if [ "$SHOWN_COUNT" -lt 50 ] && [ "$LOW_COUNT" -gt 0 ]; then
    REMAINING=$((50 - SHOWN_COUNT))
    
    echo "" >> "$PRIORITY_FILE"
    echo "### Additional Low Severity Issues" >> "$PRIORITY_FILE"
    echo "" >> "$PRIORITY_FILE"
    
    echo "$ALL_ALERTS" | jq -r --argjson start "$SHOWN_COUNT" --argjson limit "$REMAINING" '
        [.[] | select((.rule.severity // "unknown") == "low")] | 
        sort_by(.created_at // "9999") | 
        .[0:$limit] | 
        to_entries | 
        .[] | 
        "### \($start + .key + 1). \(.value.rule.name // "Unknown") (LOW)
- **Issue #**: \(.value.number)
- **File**: `\(.value.most_recent_instance.location.path // "unknown"):\(.value.most_recent_instance.location.start_line // 0)`
- **Rule**: `\(.value.rule.id // "unknown")`
- **Issue**: \(.value.most_recent_instance.message.text // "No message available")
- **Created**: \(.value.created_at // "unknown")
"
    ' >> "$PRIORITY_FILE"
fi

echo "" >> "$PRIORITY_FILE"
echo "## Summary Statistics" >> "$PRIORITY_FILE"
echo "" >> "$PRIORITY_FILE"
echo "$ALL_ALERTS" | jq -r '
    group_by(.rule.id // "unknown") | 
    map({
        rule: (.[0].rule.id // "unknown"),
        name: (.[0].rule.name // "Unknown"),
        severity: (.[0].rule.severity // "unknown"),
        count: length
    }) | 
    sort_by(.count) | 
    reverse | 
    .[0:10] | 
    .[] | 
    "- **\(.name)** (\(.rule)): \(.count) occurrences - \(.severity | ascii_upcase)"
' >> "$PRIORITY_FILE"

echo ""
echo "✅ Security report generated successfully!"
echo ""
echo "📄 Files created:"
echo "  - $OUTPUT_FILE (Full markdown report)"
echo "  - $CSV_FILE (CSV summary)"
echo "  - $PRIORITY_FILE (Top 50 priority fixes)"
echo "  - $JSON_FILE (Raw JSON data)"
echo ""
echo "📊 Quick Summary:"
echo "$ALL_ALERTS" | jq -r '
    group_by(.rule.severity) | 
    map({severity: (.[0].rule.severity // "unknown"), count: length}) | 
    sort_by(.severity) | 
    reverse | 
    .[] | 
    "  - \(.severity | ascii_upcase): \(.count)"
'

echo ""
echo "🔍 Preview of top issues:"
echo "$ALL_ALERTS" | jq -r '
    [.[] | select((.rule.severity // "unknown") == "critical" or (.rule.severity // "unknown") == "high" or (.rule.severity // "unknown") == "medium")] | 
    .[0:3] | 
    .[] | 
    "  - \(.rule.name // "Unknown") in \(.most_recent_instance.location.path // "unknown" | split("/") | .[-1]):\(.most_recent_instance.location.start_line // 0)"
'