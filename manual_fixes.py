#!/usr/bin/env python3
import re

def manual_fixes(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Fix lambda assertions - these need special handling
    fixes = [
        # Lambda within assertThat
        (r'assertThat\(result\[0\]\.getTransactions\(\)\.size\)\.isGreaterThanOrEqualTo\(2\)',
         'expect(result[0].getTransactions().size).toBeGreaterThanOrEqualTo(2)'),

        (r'assertThat\(result\[0\]\.getTransactions\(\)\.last\(\)\.amount\)\.isGreaterThan\(0\.0\)',
         'expect(result[0].getTransactions().last().amount).toBeGreaterThan(0.0)'),

        (r'assertThat\(expectedXirrCondition\(xirrValue\)\)\.isTrue\(\)',
         'expect(expectedXirrCondition(xirrValue)).toEqual(true)'),

        (r'assertThat\(endDates\[1\]\.toEpochDay\(\) - endDates\[0\]\.toEpochDay\(\)\)\.isEqualTo\(15\)',
         'expect(endDates[1].toEpochDay() - endDates[0].toEpochDay()).toEqual(15)'),

        # In lambda functions
        (r'assertThat\(xirr\.calculate\(\)\)\.isGreaterThan\(-1\.0\)',
         'expect(xirr.calculate()).toBeGreaterThan(-1.0)'),

        (r'assertThat\(xirr\.getTransactions\(\)\.last\(\)\.amount\)\.isGreaterThan\(0\.0\)',
         'expect(xirr.getTransactions().last().amount).toBeGreaterThan(0.0)'),

        (r'assertThat\(xirr\.calculate\(\)\)\.isLessThanOrEqualTo\(0\.99\)',
         'expect(xirr.calculate()).toBeLessThanOrEqualTo(0.99)'),

        (r'assertThat\(correspondingXirr\.calculate\(\)\)\.isGreaterThan\(-1\.0\)',
         'expect(correspondingXirr.calculate()).toBeGreaterThan(-1.0)'),

        # Result failures
        (r'assertThat\(result\.failedCalculations\)\.allMatch \{ it\.contains\("Failed for date"\) \}',
         'result.failedCalculations.forEach { expect(it).toContain("Failed for date") }'),

        # expectedBehavior(xirr)
        (r'assertThat\(expectedBehavior\(xirr\)\)\.isTrue\(\)',
         'expect(expectedBehavior(xirr)).toEqual(true)'),

        # Specific SummaryServiceTest fixes
        (r'assertThat\(summary\.totalProfit\)\n\s+\.isEqualByComparingTo\("',
         'expect(summary.totalProfit).toEqual("'),

        (r'assertThat\(summary\.earningsPerDay\)\n\s+\.isEqualByComparingTo\(',
         'expect(summary.earningsPerDay).toEqual('),

        (r'assertThat\(summary\.entryDate\)\n\s+\.isEqualTo\(',
         'expect(summary.entryDate).toEqual('),

        (r'assertThat\(summary\.totalValue\)\n\s+\.isEqualByComparingTo\(',
         'expect(summary.totalValue).toEqual('),

        (r'assertThat\(summary\.xirrAnnualReturn\)\n\s+\.isEqualByComparingTo\(',
         'expect(summary.xirrAnnualReturn).toEqual('),

        (r'assertThat\(count\)\n\s+\.isZero\(\)',
         'expect(count).toEqual(0)'),

        (r'assertThat\(result\)\n\s+\.hasSize\(',
         'expect(result).toHaveSize('),

        (r'assertThat\(result\.content\)\n\s+\.hasSize\(',
         'expect(result.content).toHaveSize('),

        (r'assertThat\(processedDates\)\n\s+\.containsExactlyInAnyOrder\(',
         'expect(processedDates).toContain.inAnyOrder.only.values('),

        (r'assertThat\(summary\.totalValue\.setScale\(2, RoundingMode\.HALF_UP\)\)\n\s+\.isEqualByComparingTo\(',
         'expect(summary.totalValue.setScale(2, RoundingMode.HALF_UP)).toEqual('),

        (r'assertThat\(summary\.totalProfit\.setScale\(2, RoundingMode\.HALF_UP\)\)\n\s+\.isEqualByComparingTo\(',
         'expect(summary.totalProfit.setScale(2, RoundingMode.HALF_UP)).toEqual('),

        # TransactionServiceTest fixes
        (r'assertThat\(totalUnrealizedProfit\)\.toEqual\(expectedTotalProfit\)\)',
         'expect(totalUnrealizedProfit).toEqual(expectedTotalProfit)'),

        (r'assertThat\(buy1\.remainingQuantity\.add\(buy2\.remainingQuantity\)\)\n\s+\.isEqualByComparingTo\(',
         'expect(buy1.remainingQuantity.add(buy2.remainingQuantity)).toEqual('),

        (r'assertThat\(profitRatio1\)\.toEqual\(BigDecimal\("0\.4"\)\)',
         'expect(profitRatio1).toEqual(BigDecimal("0.4"))'),

        (r'assertThat\(profitRatio2\)\.toEqual\(BigDecimal\("0\.6"\)\)',
         'expect(profitRatio2).toEqual(BigDecimal("0.6"))'),

        (r'assertThat\(buy1\.unrealizedProfit\.add\(buy2\.unrealizedProfit\)\)\.isGreaterThan\(BigDecimal\.ZERO\)',
         'expect(buy1.unrealizedProfit.add(buy2.unrealizedProfit)).toBeGreaterThan(BigDecimal.ZERO)'),
    ]

    for pattern, replacement in fixes:
        content = re.sub(pattern, replacement, content, flags=re.MULTILINE)

    with open(filepath, 'w') as f:
        f.write(content)

    print(f"Applied manual fixes to {filepath}")

if __name__ == '__main__':
    files = [
        'src/test/kotlin/ee/tenman/portfolio/service/CalculationServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/InvestmentMetricsServiceTest.kt',
        'src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt',
    ]

    for filepath in files:
        manual_fixes(filepath)

    print("All manual fixes complete!")
