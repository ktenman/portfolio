#!/bin/bash

# Batch migration script for Atrium assertions

FILES=(
  "src/test/kotlin/ee/tenman/portfolio/service/CalculationServiceTest.kt"
  "src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt"
  "src/test/kotlin/ee/tenman/portfolio/service/InvestmentMetricsServiceTest.kt"
  "src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt"
  "src/test/kotlin/ee/tenman/portfolio/service/DailyPriceServiceTest.kt"
)

for file in "${FILES[@]}"; do
  echo "Processing $file..."

  # Backup
  cp "$file" "$file.bak"

  # Replace imports
  sed -i '' 's/import org.assertj.core.api.Assertions.assertThat/import ch.tutteli.atrium.api.fluent.en_GB.*\nimport ch.tutteli.atrium.api.verbs.expect/g' "$file"
  sed -i '' '/^import org.assertj.core.api.Assertions.within$/d' "$file"
  sed -i '' '/^import org.assertj.core.api.Assertions.assertThatThrownBy$/d' "$file"

  # Replace assertions - basic patterns
  sed -i '' 's/assertThat(\([^)]*\))\.isEqualTo(\([^)]*\))/expect(\1).toEqual(\2)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isEqualByComparingTo(\([^)]*\))/expect(\1).toEqual(\2)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isNotNull()/expect(\1).notToEqualNull()/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isNull()/expect(\1).toEqual(null)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isEmpty()/expect(\1).toBeEmpty()/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isNotEmpty()/expect(\1).notToBeEmpty()/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isZero()/expect(\1).toEqual(0)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isTrue()/expect(\1).toEqual(true)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.hasSize(\([^)]*\))/expect(\1).toHaveSize(\2)/g' "$file"

  # Replace comparison assertions
  sed -i '' 's/assertThat(\([^)]*\))\.isGreaterThan(\([^)]*\))/expect(\1).toBeGreaterThan(\2)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isGreaterThanOrEqualTo(\([^)]*\))/expect(\1).toBeGreaterThanOrEqualTo(\2)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isLessThan(\([^)]*\))/expect(\1).toBeLessThan(\2)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isLessThanOrEqualTo(\([^)]*\))/expect(\1).toBeLessThanOrEqualTo(\2)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isNegative()/expect(\1).toBeLessThan(0)/g' "$file"
  sed -i '' 's/assertThat(\([^)]*\))\.isPositive()/expect(\1).toBeGreaterThan(0)/g' "$file"

  # Replace test method names to add "should"
  sed -i '' 's/fun `\([^s][^h][^o][^u][^l][^d]\)/fun `should \1/g' "$file"

  echo "Completed $file"
done

echo "Migration complete!"
