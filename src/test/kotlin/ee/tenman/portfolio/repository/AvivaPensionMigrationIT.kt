package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

@IntegrationTest
class AvivaPensionMigrationIT {
  @Resource
  private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `should have applied aviva instrument migration successfully`() {
    val applied =
      jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
        Int::class.java,
        "202606111301",
      )
    expect(applied).toEqual(1)
  }

  @Test
  fun `should have applied aviva transactions migration successfully`() {
    val applied =
      jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
        Int::class.java,
        "202606111302",
      )
    expect(applied).toEqual(1)
  }

  @Test
  fun `should accept aviva instrument row with gbp fund currency`() {
    jdbcTemplate.update(
      """
      INSERT INTO instrument (
        symbol, name, instrument_category, base_currency, fund_currency,
        provider_name, current_price, created_at, updated_at, version
      ) VALUES (
        'GB00B0ZDNB53:GBP', 'Aviva BlackRock Aquila US Equity Index Tracker 6 Pension Fund', 'ETF', 'EUR', 'GBP',
        'FT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
      )
      """.trimIndent(),
    )
    val row =
      jdbcTemplate.queryForMap(
        "SELECT name, instrument_category, base_currency, fund_currency, provider_name FROM instrument WHERE symbol = ?",
        "GB00B0ZDNB53:GBP",
      )
    expect(row["name"] as String).toEqual("Aviva BlackRock Aquila US Equity Index Tracker 6 Pension Fund")
    expect(row["instrument_category"] as String).toEqual("ETF")
    expect(row["base_currency"] as String).toEqual("EUR")
    expect(row["fund_currency"] as String).toEqual("GBP")
    expect(row["provider_name"] as String).toEqual("FT")
  }

  @Test
  fun `should accept aviva transaction row with eur price expression`() {
    jdbcTemplate.update(
      """
      INSERT INTO instrument (
        symbol, name, instrument_category, base_currency, fund_currency,
        provider_name, current_price, created_at, updated_at, version
      ) VALUES (
        'GB00B0ZDNB53:GBP', 'Aviva BlackRock Aquila US Equity Index Tracker 6 Pension Fund', 'ETF', 'EUR', 'GBP',
        'FT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
      )
      """.trimIndent(),
    )
    jdbcTemplate.update(
      """
      INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission, remaining_quantity)
      VALUES ((SELECT id FROM instrument WHERE symbol = 'GB00B0ZDNB53:GBP'), 'BUY', 731.471458774, 3459.86 / 0.84828 / 731.471458774, '2020-01-02', 'AVIVA', 0, 731.471458774)
      """.trimIndent(),
    )
    val row =
      jdbcTemplate.queryForMap(
        "SELECT ROUND(price * quantity, 2) AS cost, remaining_quantity FROM portfolio_transaction WHERE platform = 'AVIVA'",
      )
    expect((row["remaining_quantity"] as BigDecimal).compareTo(BigDecimal("731.471458774"))).toEqual(0)
    expect((row["cost"] as BigDecimal).compareTo(BigDecimal("4078.68"))).toEqual(0)
  }
}
