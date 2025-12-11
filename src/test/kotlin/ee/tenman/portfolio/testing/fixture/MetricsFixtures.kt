package ee.tenman.portfolio.testing.fixture

import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import net.datafaker.Faker
import java.math.BigDecimal

object MetricsFixtures {
  private val faker = Faker()

  fun createMetrics(
    totalInvestment: BigDecimal = BigDecimal("1000"),
    currentValue: BigDecimal = BigDecimal("1500"),
    profit: BigDecimal = BigDecimal("500"),
    realizedProfit: BigDecimal = BigDecimal.ZERO,
    unrealizedProfit: BigDecimal = BigDecimal("500"),
    xirr: Double = 25.0,
    quantity: BigDecimal = BigDecimal("10"),
  ): InstrumentMetrics =
    InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = profit,
      realizedProfit = realizedProfit,
      unrealizedProfit = unrealizedProfit,
      xirr = xirr,
      quantity = quantity,
    )

  fun createRandomMetrics(): InstrumentMetrics =
    createMetrics(
      totalInvestment = BigDecimal(faker.number().randomDouble(2, 100, 10000)),
      currentValue = BigDecimal(faker.number().randomDouble(2, 100, 15000)),
      profit = BigDecimal(faker.number().randomDouble(2, -1000, 5000)),
      unrealizedProfit = BigDecimal(faker.number().randomDouble(2, -500, 5000)),
      xirr = faker.number().randomDouble(2, -50, 100),
      quantity = BigDecimal(faker.number().randomDouble(2, 1, 100)),
    )
}
