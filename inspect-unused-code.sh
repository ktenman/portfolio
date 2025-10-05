#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$PROJECT_DIR/build/inspection-results"
INSPECTION_PROFILE="$PROJECT_DIR/.idea/inspectionProfiles/Unused_Code.xml"

echo "════════════════════════════════════════════════════════════════"
echo "  IntelliJ IDEA Unused Code Inspector"
echo "════════════════════════════════════════════════════════════════"
echo ""

find_idea_command() {
    local idea_cmd=""

    if [[ "$OSTYPE" == "darwin"* ]]; then
        local idea_app="/Applications/IntelliJ IDEA.app"
        if [ -d "$idea_app" ]; then
            idea_cmd="$idea_app/Contents/MacOS/idea"
        fi

        if [ ! -f "$idea_cmd" ]; then
            idea_app="/Applications/IntelliJ IDEA CE.app"
            if [ -d "$idea_app" ]; then
                idea_cmd="$idea_app/Contents/MacOS/idea"
            fi
        fi
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        if command -v idea &> /dev/null; then
            idea_cmd="idea"
        elif command -v idea.sh &> /dev/null; then
            idea_cmd="idea.sh"
        fi
    fi

    echo "$idea_cmd"
}

create_inspection_profile() {
    echo "Creating inspection profile..."

    mkdir -p "$(dirname "$INSPECTION_PROFILE")"

    cat > "$INSPECTION_PROFILE" << 'EOF'
<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="Unused Code" />
    <inspection_tool class="unused" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="LOCAL_VARIABLE" value="true" />
      <option name="FIELD" value="true" />
      <option name="METHOD" value="true" />
      <option name="CLASS" value="true" />
      <option name="PARAMETER" value="true" />
      <option name="REPORT_PARAMETER_FOR_PUBLIC_METHODS" value="true" />
      <option name="ADD_MAINS_TO_ENTRIES" value="true" />
      <option name="ADD_APPLET_TO_ENTRIES" value="true" />
      <option name="ADD_SERVLET_TO_ENTRIES" value="true" />
      <option name="ADD_NONJAVA_TO_ENTRIES" value="true" />
    </inspection_tool>
    <inspection_tool class="UnusedDeclaration" enabled="true" level="WARNING" enabled_by_default="true" />
    <inspection_tool class="UNUSED_SYMBOL" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="LOCAL_VARIABLE" value="true" />
      <option name="PARAMETER" value="true" />
    </inspection_tool>
  </profile>
</component>
EOF

    echo "✅ Inspection profile created at: $INSPECTION_PROFILE"
}

run_inspection() {
    local idea_cmd=$(find_idea_command)

    if [ -z "$idea_cmd" ] || [ ! -f "$idea_cmd" ]; then
        echo "❌ ERROR: IntelliJ IDEA not found!"
        echo ""
        echo "Please install IntelliJ IDEA or set up the command-line launcher:"
        echo "  Tools → Create Command-line Launcher..."
        echo ""
        echo "Or run inspections manually in IntelliJ:"
        echo "  Code → Inspect Code → Select 'Unused Code' profile"
        exit 1
    fi

    echo "Found IntelliJ IDEA: $idea_cmd"
    echo ""

    if [ ! -f "$INSPECTION_PROFILE" ]; then
        create_inspection_profile
        echo ""
    fi

    echo "Running inspection..."
    echo "  Project: $PROJECT_DIR"
    echo "  Profile: $INSPECTION_PROFILE"
    echo "  Output: $OUTPUT_DIR"
    echo ""

    rm -rf "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"

    "$idea_cmd" inspect \
        "$PROJECT_DIR" \
        "$INSPECTION_PROFILE" \
        "$OUTPUT_DIR" \
        -v2 \
        -d "$PROJECT_DIR/src" \
        2>&1 | grep -v "WARNING:" || true

    echo ""
}

parse_results() {
    echo "════════════════════════════════════════════════════════════════"
    echo "  INSPECTION RESULTS"
    echo "════════════════════════════════════════════════════════════════"
    echo ""

    local unused_count=0
    local files_with_issues=0

    if [ -f "$OUTPUT_DIR/UnusedDeclaration.xml" ] || [ -f "$OUTPUT_DIR/unused.xml" ] || [ -f "$OUTPUT_DIR/UNUSED_SYMBOL.xml" ]; then
        for xml_file in "$OUTPUT_DIR"/*.xml; do
            if [ -f "$xml_file" ] && grep -q "<problem" "$xml_file" 2>/dev/null; then
                local count=$(grep -c "<problem" "$xml_file" || echo 0)
                if [ "$count" -gt 0 ]; then
                    ((unused_count+=count))
                    ((files_with_issues++))

                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    echo "$(basename "$xml_file" .xml)"
                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

                    grep -A 10 "<problem" "$xml_file" | while IFS= read -r line; do
                        if echo "$line" | grep -q "<file>"; then
                            file=$(echo "$line" | sed 's/.*>\(.*\)<.*/\1/' | sed "s|file://\$PROJECT_DIR\$|.|g")
                            printf "📄 %s" "$file"
                        elif echo "$line" | grep -q "<line>"; then
                            line_num=$(echo "$line" | sed 's/.*>\(.*\)<.*/\1/')
                            printf ":%s\n" "$line_num"
                        elif echo "$line" | grep -q "<description>"; then
                            desc=$(echo "$line" | sed 's/.*>\(.*\)<.*/\1/' | sed 's/&apos;/'\''/g' | sed 's/&quot;/"/g')
                            echo "   ⚠️  $desc"
                            echo ""
                        fi
                    done
                fi
            fi
        done
    fi

    echo "════════════════════════════════════════════════════════════════"
    echo "  SUMMARY"
    echo "════════════════════════════════════════════════════════════════"
    echo ""

    if [ "$unused_count" -gt 0 ]; then
        echo "❌ Found $unused_count unused declarations"
        echo ""
        echo "Full HTML report: $OUTPUT_DIR/index.html"
        echo ""
        return 1
    else
        echo "✅ No unused code found!"
        echo ""
        return 0
    fi
}

main() {
    run_inspection
    parse_results
}

main
