package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.sql.Connection
import javax.sql.DataSource

@IntegrationTest
class InstrumentLogoMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `gives every existing and newly inserted instrument its own uuid`() {
    dataSource.connection.use { connection ->
      seed(
        connection,
        "CREATE TABLE instrument (id BIGINT PRIMARY KEY, symbol VARCHAR(50))",
        "INSERT INTO instrument VALUES (1, 'ÕUNAD:TLN:EUR'), (2, 'BTCEUR')",
      )
      ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V202610281508__add_logo_to_instrument.sql"))
      connection.createStatement().use { it.execute("INSERT INTO instrument (id, symbol) VALUES (3, 'VWCE:GER:EUR')") }
      val counts =
        listOf(
          count(connection, "SELECT COUNT(DISTINCT uuid) FROM instrument"),
          count(connection, "SELECT COUNT(*) FROM instrument WHERE uuid IS NULL"),
          count(connection, "SELECT COUNT(*) FROM instrument WHERE logo_source IS NULL"),
        )
      connection.rollback()
      expect(counts).toEqual(listOf(3, 0, 3))
    }
  }

  @Test
  fun `clears the wrongly picked logos so the job collects them again`() {
    dataSource.connection.use { connection ->
      seed(
        connection,
        "CREATE TABLE instrument (id BIGINT PRIMARY KEY, symbol VARCHAR(50), logo_source VARCHAR(20))",
        "INSERT INTO instrument VALUES (1, 'BNKE:PAR:EUR', 'LLM_SELECTED'), (2, 'GB00B0ZDNB53:GBP', 'LLM_SELECTED'), " +
          "(3, 'ÖSTPOST:VIE:EUR', 'LLM_SELECTED'), (4, 'VWCE:GER:EUR', 'LIGHTYEAR')",
      )
      ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V202610291115__recollect_wrong_instrument_logos.sql"))
      val cleared = count(connection, "SELECT COUNT(*) FROM instrument WHERE logo_source IS NULL AND id IN (1, 2)")
      val kept = count(connection, "SELECT COUNT(*) FROM instrument WHERE logo_source IS NOT NULL AND id IN (3, 4)")
      connection.rollback()
      expect(listOf(cleared, kept)).toEqual(listOf(2, 2))
    }
  }

  private fun seed(
    connection: Connection,
    vararg statements: String,
  ) {
    connection.autoCommit = false
    connection.createStatement().use { statement ->
      statement.execute("DROP SCHEMA IF EXISTS instrument_logo_seed CASCADE")
      statement.execute("CREATE SCHEMA instrument_logo_seed")
      statement.execute("SET LOCAL search_path TO instrument_logo_seed")
      statements.forEach { statement.execute(it) }
    }
  }

  private fun count(
    connection: Connection,
    sql: String,
  ): Int =
    connection.createStatement().use { statement ->
      statement.executeQuery(sql).use { result ->
        result.next()
        result.getInt(1)
      }
    }
}
