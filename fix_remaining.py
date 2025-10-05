#!/usr/bin/env python3
import re

def fix_remaining_assertions(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Fix assertThat calls with complex patterns
    replacements = [
        # notToEqualNull checks
        (r'assertThat\(([^)]+)\)\.isNotNull', r'expect(\1).notToEqualNull()'),

        # Collection operations
        (r'assertThat\(([^)]+)\)\.allMatch\(([^)]+)\)', r'expect(\1).all(\2)'),

        # Chained assertions that were missed
        (r'assertThat\(([^)]+)\)\.isGreaterThan\(([^)]+)\)', r'expect(\1).toBeGreaterThan(\2)'),

        # Contains operations
        (r'assertThat\(([^)]+)\)\.contains\(([^)]+)\)', r'expect(\1).toContain(\2)'),
        (r'assertThat\(([^)]+)\)\.containsExactlyInAnyOrder\(([^)]+)\)', r'expect(\1).toContain.inAnyOrder.only.values(\2)'),
        (r'assertThat\(([^)]+)\)\.doesNotContain\(([^)]+)\)', r'expect(\1).notToContain(\2)'),

        # Size operations
        (r'assertThat\(([^)]+)\)\.hasSize\(([^)]+)\)', r'expect(\1).toHaveSize(\2)'),
        (r'assertThat\(([^)]+)\)\.hasSizeGreaterThan\(([^)]+)\)', r'expect(\1).toHaveSize.toBeGreaterThan(\2)'),

        # Equality checks that were missed
        (r'assertThat\(([^)]+)\)\.isEqualTo\(([^)]+)\)', r'expect(\1).toEqual(\2)'),
        (r'assertThat\(([^)]+)\)\.isEqualByComparingTo\(([^)]+)\)', r'expect(\1).toEqual(\2)'),

        # Comparison operators
        (r'assertThat\(([^)]+)\)\.isLessThanOrEqualTo\(([^)]+)\)', r'expect(\1).toBeLessThanOrEqualTo(\2)'),
        (r'assertThat\(([^)]+)\)\.isGreaterThanOrEqualTo\(([^)]+)\)', r'expect(\1).toBeGreaterThanOrEqualTo(\2)'),
        (r'assertThat\(([^)]+)\)\.isLessThan\(([^)]+)\)', r'expect(\1).toBeLessThan(\2)'),

        # Boolean checks
        (r'assertThat\(([^)]+)\)\.isTrue\(\)', r'expect(\1).toEqual(true)'),
        (r'assertThat\(([^)]+)\)\.isZero\(\)', r'expect(\1).toEqual(0)'),

        # Complex chained assertions with .and
        (r'\.isCloseTo\(([^,]+),\s*within\(([^)]+)\)\)', r'.toEqual(\1)'),
    ]

    for pattern, replacement in replacements:
        content = re.sub(pattern, replacement, content)

    with open(filepath, 'w') as f:
        f.write(content)

    print(f"Fixed {filepath}")

if __name__ == '__main__':
    files = [
        'src/test/kotlin/ee/tenman/portfolio/service/CalculationServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/InvestmentMetricsServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/DailyPriceServiceTest.kt',
    ]

    for filepath in files:
        fix_remaining_assertions(filepath)

    print("All remaining assertions fixed!")
