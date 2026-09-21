package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.math.BigDecimal
import javax.sql.DataSource

@IntegrationTest
class InstrumentMinutePriceMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should seed completed matching provider snapshot hours within nine days`() {
    dataSource.connection.use { connection ->
      connection.autoCommit = false
      connection.createStatement().use { statement ->
        statement.execute("DROP SCHEMA IF EXISTS minute_price_seed CASCADE")
        statement.execute("CREATE SCHEMA minute_price_seed")
        statement.execute("SET LOCAL search_path TO minute_price_seed")
      }
      connection.createStatement().use { statement ->
        statement.execute("CREATE TABLE instrument (id BIGINT PRIMARY KEY, provider_name VARCHAR(50))")
        statement.execute(
          "CREATE TABLE price_snapshot " +
            "(instrument_id BIGINT, provider_name VARCHAR(50), snapshot_hour TIMESTAMPTZ, price NUMERIC(20, 10))",
        )
        statement.execute("INSERT INTO instrument VALUES (1, 'LIGHTYEAR'), (2, 'FT')")
        statement.execute(
          """
          INSERT INTO price_snapshot VALUES
            (1, 'LIGHTYEAR', date_trunc('hour', now()) - INTERVAL '1 hour', 101),
            (1, 'FT', date_trunc('hour', now()) - INTERVAL '2 hours', 102),
            (1, 'LIGHTYEAR', date_trunc('hour', now()), 103),
            (2, 'FT', date_trunc('hour', now()) - INTERVAL '8 days', 201),
            (2, 'FT', date_trunc('hour', now()) - INTERVAL '10 days', 202)
          """,
        )
      }
      ScriptUtils.executeSqlScript(
        connection,
        ClassPathResource("db/migration/V202609211200__add_instrument_minute_price_table.sql"),
      )
      val rows =
        connection.createStatement().use { statement ->
          statement
            .executeQuery(
            "SELECT instrument_id, captured_at, price FROM instrument_minute_price ORDER BY instrument_id",
          ).use { result ->
            generateSequence { if (result.next()) result else null }
              .map {
                Triple(
                  it.getLong("instrument_id"),
                  it.getTimestamp("captured_at").toInstant(),
                  it.getBigDecimal("price"),
                )
              }.toList()
          }
        }
      connection.rollback()
      expect(rows).toHaveSize(2)
      expect(rows.map { it.first }).toEqual(listOf(1L, 2L))
      expect(rows[0].third).toEqualNumerically(BigDecimal("101"))
      expect(rows[1].third).toEqualNumerically(BigDecimal("201"))
      expect(rows.all { it.second.epochSecond % 3600 == 3540L }).toEqual(true)
    }
  }
}
