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
class EtfHoldingIndustryMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should preserve legacy industries with provenance and an unchanged LLM backup`() {
    val results =
      dataSource.connection.use { connection ->
        connection.autoCommit = false
        runCatching { migrate(connection) }.also { connection.rollback() }.getOrThrow()
      }
    expect(results.first).toEqual(
      listOf(
        listOf("PERSONAL_CARE_PRODUCTS", "GPT_5_6_LUNA", "LLM", null, "7"),
        listOf("MACHINERY", null, "UNKNOWN", null, "3"),
        listOf(null, null, null, null, "0"),
      ),
    )
    expect(results.second).toEqual(
      listOf(listOf("00000000-0000-0000-0000-000000000001", "PERSONAL_CARE_PRODUCTS", "GPT_5_6_LUNA", "7", "true", "true")),
    )
  }

  private fun migrate(connection: Connection): Pair<List<List<String?>>, List<List<String?>>> {
    prepare(connection)
    ScriptUtils.executeSqlScript(
      connection,
      ClassPathResource("db/migration/V202609231932__add_holding_industry_provenance.sql"),
    )
    val holdings =
      query(
        connection,
        "SELECT industry, industry_classified_by_model, industry_source, industry_effective_date, version FROM etf_holding ORDER BY uuid",
      )
    val backup =
      query(
        connection,
        """
        SELECT holding_uuid, industry, industry_classified_by_model, holding_version,
               holding_updated_at = TIMESTAMPTZ '2026-09-01 12:00:00Z', backed_up_at IS NOT NULL
        FROM etf_holding_industry_backup
        """,
      )
    return holdings to backup
  }

  private fun prepare(connection: Connection) {
    connection.createStatement().use { statement ->
      val schema = "industry_${UUID.randomUUID().toString().replace("-", "")}"
      statement.execute("CREATE SCHEMA $schema")
      statement.execute("SET LOCAL search_path TO $schema")
      statement.execute(
        """
        CREATE TABLE etf_holding (
          uuid UUID PRIMARY KEY, industry VARCHAR(150), industry_classified_by_model VARCHAR(100),
          version BIGINT NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        );
        INSERT INTO etf_holding VALUES
          ('00000000-0000-0000-0000-000000000001', 'PERSONAL_CARE_PRODUCTS', 'GPT_5_6_LUNA', 7, '2026-09-01 12:00:00Z'),
          ('00000000-0000-0000-0000-000000000002', 'MACHINERY', NULL, 3, '2026-09-02 12:00:00Z'),
          ('00000000-0000-0000-0000-000000000003', NULL, NULL, 0, '2026-09-03 12:00:00Z')
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
