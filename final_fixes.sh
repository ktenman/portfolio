#!/bin/bash

# Fix nullable BigDecimal comparisons - add !! for non-null assertion
file1="src/test/kotlin/ee/tenman/portfolio/service/TransactionServiceTest.kt"

# For nullable BigDecimal, we need to use !! or handle nullability
# Lines 85, 152, 189, 269, 270, 281, 347, 459, 648, 690

# Fix notToEqualNull usage - these need to just check for not null with a simpler assertion
# Since unrealizedProfit, realizedProfit, etc. are non-null BigDecimal in context, we can use notToEqualNull correctly

# Fix String vs BigDecimal - convert strings to BigDecimal
file2="src/test/kotlin/ee/tenman/portfolio/service/SummaryServiceTest.kt"

# Fix lines with string instead of BigDecimal
sed -i '' 's/\.toEqual("25015\.03")/\.toEqual(BigDecimal("25015.03"))/g' "$file2"
sed -i '' 's/\.toEqual("0E-10")/\.toEqual(BigDecimal("0E-10"))/g' "$file2"
sed -i '' 's/\.toEqual("600\.00")/\.toEqual(BigDecimal("600.00"))/g' "$file2"
sed -i '' 's/\.toEqual("100\.00")/\.toEqual(BigDecimal("100.00"))/g' "$file2"
sed -i '' 's/\.toEqual("0\.07500000")/\.toEqual(BigDecimal("0.07500000"))/g' "$file2"
sed -i '' 's/\.toEqual("1560\.00")/\.toEqual(BigDecimal("1560.00"))/g' "$file2"
sed -i '' 's/\.toEqual("360\.00")/\.toEqual(BigDecimal("360.00"))/g' "$file2"
sed -i '' 's/\.toEqual("0\.12000000")/\.toEqual(BigDecimal("0.12000000"))/g' "$file2"
sed -i '' 's/\.toEqual("2225\.00")/\.toEqual(BigDecimal("2225.00"))/g' "$file2"
sed -i '' 's/\.toEqual("175\.00")/\.toEqual(BigDecimal("175.00"))/g' "$file2"
sed -i '' 's/\.toEqual("0\.08000000")/\.toEqual(BigDecimal("0.08000000"))/g' "$file2"
sed -i '' 's/\.toEqual("1200\.00")/\.toEqual(BigDecimal("1200.00"))/g' "$file2"
sed -i '' 's/\.toEqual("200\.00")/\.toEqual(BigDecimal("200.00"))/g' "$file2"
sed -i '' 's/\.toEqual("2000\.00")/\.toEqual(BigDecimal("2000.00"))/g' "$file2"
sed -i '' 's/\.toEqual("2100\.00")/\.toEqual(BigDecimal("2100.00"))/g' "$file2"
sed -i '' 's/\.toEqual("150\.00")/\.toEqual(BigDecimal("150.00"))/g' "$file2"
sed -i '' 's/\.toEqual("-1762\.39")/\.toEqual(BigDecimal("-1762.39"))/g' "$file2"

# Now fix nullable BigDecimal issues - we need to handle that these might be nullable
# For unrealizedProfit, realizedProfit - these should not be null in context, so use !!
sed -i '' 's/expect(sellTx\.realizedProfit\.compareTo(BigDecimal\.ZERO))/expect(sellTx.realizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(buyTx\.unrealizedProfit\.compareTo(BigDecimal\.ZERO))/expect(buyTx.unrealizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(buy1\.unrealizedProfit\.compareTo(BigDecimal\.ZERO))/expect(buy1.unrealizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(buy2\.unrealizedProfit\.compareTo(BigDecimal\.ZERO))/expect(buy2.unrealizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(sell1\.realizedProfit\.compareTo(BigDecimal\.ZERO))/expect(sell1.realizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(sell2\.realizedProfit\.compareTo(BigDecimal\.ZERO))/expect(sell2.realizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(singleBuy\.unrealizedProfit\.compareTo(BigDecimal\.ZERO))/expect(singleBuy.unrealizedProfit!!.compareTo(BigDecimal.ZERO))/g' "$file1"
sed -i '' 's/expect(buyTx\.remainingQuantity\.compareTo(BigDecimal\.ZERO))/expect(buyTx.remainingQuantity!!.compareTo(BigDecimal.ZERO))/g' "$file1"

# Fix notToEqualNull - just check that the value is not null
sed -i '' 's/expect(sellTx\.averageCost)\.notToEqualNull()/expect(sellTx.averageCost).notToEqualNull()/g' "$file1"
sed -i '' 's/expect(tx1\.unrealizedProfit)\.notToEqualNull()/expect(tx1.unrealizedProfit).notToEqualNull()/g' "$file1"
sed -i '' 's/expect(tx2\.unrealizedProfit)\.notToEqualNull()/expect(tx2.unrealizedProfit).notToEqualNull()/g' "$file1"
sed -i '' 's/expect(tx3\.unrealizedProfit)\.notToEqualNull()/expect(tx3.unrealizedProfit).notToEqualNull()/g' "$file1"
sed -i '' 's/expect(result)\.notToEqualNull()/expect(result).notToEqualNull()/g' "$file1"

echo "Applied final fixes"
