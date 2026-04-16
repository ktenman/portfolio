package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

@IntegrationTest
class BnkeInstrumentMigrationIT {
  @Resource
  private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `should have applied BNKE FR instrument migration successfully`() {
    val applied =
      jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
        Int::class.java,
        "202604161200",
      )
    expect(applied).toEqual(1)
  }

  @Test
  fun `should accept BNKE FR instrument row with correct schema`() {
    jdbcTemplate.update(
      """
      INSERT INTO instrument (
        symbol, name, instrument_category, base_currency,
        provider_name, provider_external_id, current_price,
        created_at, updated_at, version
      ) VALUES (
        'BNKE:PAR:EUR', 'Amundi Euro Stoxx Banks UCITS ETF Acc', 'ETF', 'EUR',
        'TRADING212', 'BNKEp_EQ', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
      )
      """.trimIndent(),
    )
    val row =
      jdbcTemplate.queryForMap(
        "SELECT name, instrument_category, base_currency, provider_name, provider_external_id FROM instrument WHERE symbol = ?",
        "BNKE:PAR:EUR",
      )
    expect(row["name"] as String).toEqual("Amundi Euro Stoxx Banks UCITS ETF Acc")
    expect(row["instrument_category"] as String).toEqual("ETF")
    expect(row["base_currency"] as String).toEqual("EUR")
    expect(row["provider_name"] as String).toEqual("TRADING212")
    expect(row["provider_external_id"] as String).toEqual("BNKEp_EQ")
  }
}
