package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

@IntegrationTest
class EtfHoldingCountryMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should back up original countries and sectors while adding country provenance`() {
    val result =
      dataSource.connection.use { connection ->
        connection.autoCommit = false
        runCatching { migrate(connection) }.also { connection.rollback() }.getOrThrow()
      }
    expect(result.first).toEqual(
      listOf(listOf("US", "LLM", null, "Finance", "LLM", "7"), listOf("GB", "UNKNOWN", null, "Health", "LIGHTYEAR", "3")),
    )
    expect(result.second).toEqual(
      listOf(
        listOf("US", "United States", "GPT_5_6_LUNA", "Finance", "LLM", "GPT_6_LUNA", "7", "true", "true"),
        listOf("GB", "United Kingdom", null, "Health", "LIGHTYEAR", null, "3", "true", "true"),
      ),
    )
  }

  private fun migrate(connection: Connection): Pair<List<List<String?>>, List<List<String?>>> {
    prepare(connection)
    ScriptUtils.executeSqlScript(
      connection,
      ClassPathResource("db/migration/V202609232146__add_holding_country_provenance.sql"),
    )
    val holdings =
      query(
        connection,
        """
        SELECT country_code, country_source, country_effective_date, sector, sector_source, version
        FROM etf_holding ORDER BY uuid
        """,
      )
    val backup =
      query(
        connection,
        """
        SELECT country_code, country_name, country_classified_by_model, sector, sector_source, classified_by_model,
               holding_version, holding_updated_at = TIMESTAMPTZ '2026-09-01 12:00:00Z', backed_up_at IS NOT NULL
        FROM etf_holding_country_sector_backup ORDER BY holding_uuid
        """,
      )
    return holdings to backup
  }

  private fun prepare(connection: Connection) {
    connection.createStatement().use { statement ->
      val schema = "country_${UUID.randomUUID().toString().replace("-", "")}"
      statement.execute("CREATE SCHEMA $schema")
      statement.execute("SET LOCAL search_path TO $schema")
      statement.execute(
        """
        CREATE TABLE etf_holding (
          uuid UUID PRIMARY KEY, country_code VARCHAR(2), country_name TEXT, country_classified_by_model VARCHAR(100),
          sector VARCHAR(150), sector_source VARCHAR(20), classified_by_model VARCHAR(100),
          version BIGINT NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        );
        INSERT INTO etf_holding VALUES
          ('00000000-0000-0000-0000-000000000001', 'US', 'United States', 'GPT_5_6_LUNA',
           'Finance', 'LLM', 'GPT_6_LUNA', 7, '2026-09-01 12:00:00Z'),
          ('00000000-0000-0000-0000-000000000002', 'GB', 'United Kingdom', NULL,
           'Health', 'LIGHTYEAR', NULL, 3, '2026-09-01 12:00:00Z')
        """,
      )
    }
  }

  private fun query(
    connection: Connection,
    sql: String,
  ): List<List<String?>> =
    connection.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows ->
        generateSequence { if (rows.next()) (1..rows.metaData.columnCount).map { rows.getObject(it)?.toString() } else null }.toList()
      }
    }
}
