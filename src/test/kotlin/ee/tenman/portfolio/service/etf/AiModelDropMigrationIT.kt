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
class AiModelDropMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should move every dropped model to the kept model of its vendor`() {
    val result =
      dataSource.connection.use { connection ->
        connection.autoCommit = false
        runCatching { migrate(connection) }.also { connection.rollback() }.getOrThrow()
      }
    expect(result).toEqual(
      listOf(
        listOf("GEMINI_4_ARGON", "CLAUDE_OPUS_5_5", "GPT_6_LUNA"),
        listOf("DEEPSEEK_V4_PRO", null, "GEMINI_4_ARGON"),
        listOf("GPT_6_LUNA", "CLAUDE_OPUS_5_5", null),
      ),
    )
  }

  private fun migrate(connection: Connection): List<List<String?>> {
    prepare(connection)
    ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V202610211005__drop_old_ai_models.sql"))
    val sql = "SELECT classified_by_model, country_classified_by_model, industry_classified_by_model FROM etf_holding ORDER BY uuid"
    return connection.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows -> generateSequence { if (rows.next()) (1..3).map { rows.getString(it) } else null }.toList() }
    }
  }

  private fun prepare(connection: Connection) {
    connection.createStatement().use { statement ->
      val schema = "models_${UUID.randomUUID().toString().replace("-", "")}"
      statement.execute("CREATE SCHEMA $schema")
      statement.execute("SET LOCAL search_path TO $schema")
      statement.execute(
        """
        CREATE TABLE etf_holding (
          uuid UUID PRIMARY KEY, classified_by_model VARCHAR(100), country_classified_by_model VARCHAR(100),
          industry_classified_by_model VARCHAR(100)
        );
        INSERT INTO etf_holding VALUES
          ('00000000-0000-0000-0000-000000000001', 'GEMINI_3_5_FLASH_LITE', 'CLAUDE_SONNET_4_6', 'GPT_5_6_LUNA'),
          ('00000000-0000-0000-0000-000000000002', 'DEEPSEEK_V3_2', NULL, 'GEMINI_4_ARGON'),
          ('00000000-0000-0000-0000-000000000003', 'GPT_6_LUNA', 'CLAUDE_OPUS_5', NULL)
        """,
      )
    }
  }
}
