#!/bin/bash

# For BigDecimal comparisons, we need to use compareTo
# toBeGreaterThan, toBeLessThan etc don't work directly with BigDecimal in Atrium
# We'll use explicit comparison results

# Fix TransactionServiceTest
file="src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt"

# Lines with toBeGreaterThan(BigDecimal.ZERO) or similar - we'll keep toBeGreaterThan for the comparison result
# This is a workaround for Atrium's type system with BigDecimal

# Actually, let's use a different approach - check if compareTo > 0
sed -i '' 's/expect(\([^)]*\))\.toBeGreaterThan(BigDecimal\.ZERO)/expect(\1.compareTo(BigDecimal.ZERO)).toBeGreaterThan(0)/g' "$file"
sed -i '' 's/expect(\([^)]*\))\.toBeLessThan(BigDecimal\.ZERO)/expect(\1.compareTo(BigDecimal.ZERO)).toBeLessThan(0)/g' "$file"
sed -i '' 's/expect(\([^)]*\))\.toBeGreaterThanOrEqualTo(BigDecimal\.ZERO)/expect(\1.compareTo(BigDecimal.ZERO)).toBeGreaterThanOrEqualTo(0)/g' "$file"
sed -i '' 's/expect(\([^)]*\))\.toBeLessThanOrEqualTo(BigDecimal\.ZERO)/expect(\1.compareTo(BigDecimal.ZERO)).toBeLessThanOrEqualTo(0)/g' "$file"

# Fix notToEqualNull - these are non-nullable types, so we can just compare directly
sed -i '' 's/expect(\([^)]*\))\.notToEqualNull()/expect(\1).notToEqualNull()/g' "$file"

echo "Fixed BigDecimal comparisons"
