#!/bin/bash

# Fix syntax errors in TransactionServiceTest.kt - remove extra parentheses
sed -i '' 's/expect(profitRatio1)\.toEqual(BigDecimal("0\.4")))/expect(profitRatio1).toEqual(BigDecimal("0.4"))/g' src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt
sed -i '' 's/expect(profitRatio2)\.toEqual(BigDecimal("0\.6")))/expect(profitRatio2).toEqual(BigDecimal("0.6"))/g' src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt

# Fix the mixed line in SummaryServiceTest
sed -i '' 's/expect(result)\.toHaveSize(2)$/expect(result).toHaveSize(2)/g' src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt
sed -i '' 's/^      \.isEqualTo(summaries)$/    expect(result).toEqual(summaries)/g' src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt

sed -i '' 's/expect(result\.content)\.toHaveSize(2)$/expect(result.content).toHaveSize(2)/g' src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt
sed -i '' 's/^      \.isEqualTo(summaries)$/    expect(result.content).toEqual(summaries)/g' src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt

sed -i '' 's/expect(result)\.toHaveSize(2)$/expect(result).toHaveSize(2)/g' src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt
sed -i '' 's/^      \.isEqualTo(between)$/    expect(result).toEqual(between)/g' src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt

echo "Fixed compilation errors"
