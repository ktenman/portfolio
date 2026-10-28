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
      connection.autoCommit = false
      connection.createStatement().use { statement ->
        statement.execute("DROP SCHEMA IF EXISTS instrument_logo_seed CASCADE")
        statement.execute("CREATE SCHEMA instrument_logo_seed")
        statement.execute("SET LOCAL search_path TO instrument_logo_seed")
        statement.execute("CREATE TABLE instrument (id BIGINT PRIMARY KEY, symbol VARCHAR(50))")
        statement.execute("INSERT INTO instrument VALUES (1, 'ÕUNAD:TLN:EUR'), (2, 'BTCEUR')")
      }
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
