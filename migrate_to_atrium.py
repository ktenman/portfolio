#!/usr/bin/env python3
import re
import sys

def migrate_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Remove assertj imports
    content = re.sub(r'import org\.assertj\.core\.api\.Assertions\.assertThat\n?', '', content)
    content = re.sub(r'import org\.assertj\.core\.api\.Assertions\.within\n?', '', content)
    content = re.sub(r'import org\.assertj\.core\.api\.Assertions\.assertThatThrownBy\n?', '', content)

    # Add Atrium imports after other ee.tenman imports or before org.junit
    if 'import ch.tutteli.atrium' not in content:
        # Find the insertion point (after last ee.tenman import or before first org.junit)
        import_match = re.search(r'(import ee\.tenman\..*?\n)(?!import ee\.tenman)', content, re.MULTILINE)
        if import_match:
            insert_pos = import_match.end()
            content = content[:insert_pos] + 'import ch.tutteli.atrium.api.fluent.en_GB.*\nimport ch.tutteli.atrium.api.verbs.expect\n' + content[insert_pos:]
        else:
            # Insert before first org.junit import
            junit_match = re.search(r'(import org\.junit)', content)
            if junit_match:
                insert_pos = junit_match.start()
                content = content[:insert_pos] + 'import ch.tutteli.atrium.api.fluent.en_GB.*\nimport ch.tutteli.atrium.api.verbs.expect\n' + content[insert_pos:]

    # Replace assertions with Atrium equivalents
    replacements = [
        # Basic assertions
        (r'assertThat\(([^)]+)\)\.isEqualTo\(([^)]+)\)', r'expect(\1).toEqual(\2)'),
        (r'assertThat\(([^)]+)\)\.isEqualByComparingTo\(([^)]+)\)', r'expect(\1).toEqual(\2)'),
        (r'assertThat\(([^)]+)\)\.isNotNull\(\)', r'expect(\1).notToEqualNull()'),
        (r'assertThat\(([^)]+)\)\.isNull\(\)', r'expect(\1).toEqual(null)'),
        (r'assertThat\(([^)]+)\)\.isEmpty\(\)', r'expect(\1).toBeEmpty()'),
        (r'assertThat\(([^)]+)\)\.isNotEmpty\(\)', r'expect(\1).notToBeEmpty()'),
        (r'assertThat\(([^)]+)\)\.isZero\(\)', r'expect(\1).toEqual(0)'),
        (r'assertThat\(([^)]+)\)\.isTrue\(\)', r'expect(\1).toEqual(true)'),
        (r'assertThat\(([^)]+)\)\.hasSize\(([^)]+)\)', r'expect(\1).toHaveSize(\2)'),

        # Comparisons
        (r'assertThat\(([^)]+)\)\.isGreaterThan\(([^)]+)\)', r'expect(\1).toBeGreaterThan(\2)'),
        (r'assertThat\(([^)]+)\)\.isGreaterThanOrEqualTo\(([^)]+)\)', r'expect(\1).toBeGreaterThanOrEqualTo(\2)'),
        (r'assertThat\(([^)]+)\)\.isLessThan\(([^)]+)\)', r'expect(\1).toBeLessThan(\2)'),
        (r'assertThat\(([^)]+)\)\.isLessThanOrEqualTo\(([^)]+)\)', r'expect(\1).toBeLessThanOrEqualTo(\2)'),
        (r'assertThat\(([^)]+)\)\.isNegative\(\)', r'expect(\1).toBeLessThan(0)'),
        (r'assertThat\(([^)]+)\)\.isPositive\(\)', r'expect(\1).toBeGreaterThan(0)'),
        (r'assertThat\(([^)]+)\)\.isBetween\(([^,]+),\s*([^)]+)\)', r'expect(\1).toBeGreaterThanOrEqualTo(\2).and.toBeLessThanOrEqualTo(\3)'),

        # Collections
        (r'assertThat\(([^)]+)\)\.contains\(([^)]+)\)', r'expect(\1).toContain(\2)'),
        (r'assertThat\(([^)]+)\)\.containsExactly\(([^)]+)\)', r'expect(\1).toContainExactly(\2)'),
        (r'assertThat\(([^)]+)\)\.containsExactlyInAnyOrder\(([^)]+)\)', r'expect(\1).toContain.inAnyOrder.only.values(\2)'),
        (r'assertThat\(([^)]+)\)\.doesNotContain\(([^)]+)\)', r'expect(\1).notToContain(\2)'),
        (r'assertThat\(([^)]+)\)\.hasSizeGreaterThan\(([^)]+)\)', r'expect(\1).toHaveSize.toBeGreaterThan(\2)'),
        (r'assertThat\(([^)]+)\)\.allMatch\(([^)]+)\)', r'expect(\1).all(\2)'),

        # within for BigDecimal - just remove it
        (r'\.isCloseTo\(([^,]+),\s*within\(([^)]+)\)\)', r'.toEqual(\1)'),
    ]

    for pattern, replacement in replacements:
        content = re.sub(pattern, replacement, content)

    # Fix test method names to include "should"
    # Only add "should" if it's not already there
    def add_should(match):
        method_name = match.group(1)
        if not method_name.startswith('should '):
            return f'fun `should {method_name}`'
        return match.group(0)

    content = re.sub(r'fun `([^`]+)`', add_should, content)

    with open(filepath, 'w') as f:
        f.write(content)

    print(f"Migrated {filepath}")

if __name__ == '__main__':
    files = [
        'src/test/kotlin/ee/tenman/portfolio/service/CalculationServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/InvestmentMetricsServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/DailyPriceServiceTest.kt',
    ]

    for filepath in files:
        migrate_file(filepath)

    print("All files migrated successfully!")
