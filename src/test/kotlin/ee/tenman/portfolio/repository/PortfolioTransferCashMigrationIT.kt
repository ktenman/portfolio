package ee.tenman.portfolio.repository

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
class PortfolioTransferCashMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should keep cash in transit with the source broker until reinvestment`() {
    val balances =
      withDatabase { connection ->
        migrate(connection)
        query(
          connection,
          """
          SELECT day::date,
                 SUM(CASE WHEN platform = 'TRADING212' THEN quantity * direction ELSE 0.00 END),
                 SUM(CASE WHEN platform = 'SWEDBANK' THEN quantity * direction ELSE 0.00 END)
          FROM generate_series('2025-12-15'::date, '2025-12-17'::date, '1 day') day
          JOIN (SELECT *, CASE WHEN transaction_type = 'BUY' THEN 1 ELSE -1 END direction
                FROM portfolio_transaction) transactions ON transaction_date <= day
          GROUP BY day ORDER BY day
          """,
        )
      }
    expect(balances).toEqual(
      listOf(
        listOf("2025-12-15", "13719.16", "0.00"),
        listOf("2025-12-16", "13719.16", "0.00"),
        listOf("2025-12-17", "0.00", "0.00"),
      ),
    )
  }

  @Test
  fun `should preserve all portfolio cash flows when correcting transfer attribution`() {
    val flows =
      withDatabase { connection ->
        val sql =
          "SELECT id, instrument_id, transaction_type, quantity, price, transaction_date, commission " +
            "FROM portfolio_transaction ORDER BY id"
        val before = query(connection, sql)
        migrate(connection)
        before to query(connection, sql)
      }
    expect(flows.second).toEqual(flows.first)
  }

  @Test
  fun `should leave unrelated bank and broker transactions unchanged`() {
    val transactions =
      withDatabase { connection ->
        prepareUnrelatedTransactions(connection)
        val sql = "SELECT * FROM portfolio_transaction WHERE id > 4 ORDER BY id"
        val before = query(connection, sql)
        migrate(connection)
        before to query(connection, sql)
      }
    expect(transactions.second).toEqual(transactions.first)
  }

  private fun <T> withDatabase(block: (Connection) -> T): T =
    dataSource.connection.use { connection ->
      connection.autoCommit = false
      runCatching {
        prepare(connection)
        block(connection)
      }.also { connection.rollback() }.getOrThrow()
    }

  private fun prepare(connection: Connection) {
    connection.createStatement().use { statement ->
      val schema = "transfer_${UUID.randomUUID().toString().replace("-", "")}"
      statement.execute("CREATE SCHEMA $schema")
      statement.execute("SET LOCAL search_path TO $schema")
      statement.execute(
        """
        CREATE TABLE instrument (id BIGINT PRIMARY KEY, symbol VARCHAR(50));
        CREATE TABLE portfolio_transaction (
          id BIGINT PRIMARY KEY, instrument_id BIGINT, transaction_type VARCHAR(10),
          quantity NUMERIC(20, 2), price NUMERIC(20, 2), transaction_date DATE,
          platform VARCHAR(30), commission NUMERIC(20, 2)
        );
        INSERT INTO instrument VALUES (1, 'EUR'), (2, 'QDVE:GER:EUR');
        INSERT INTO portfolio_transaction VALUES
          (1, 1, 'BUY', 13719.16, 1, '2025-12-15', 'TRADING212', 0),
          (2, 1, 'BUY', 13719.16, 1, '2025-12-16', 'SWEDBANK', 0),
          (3, 1, 'SELL', 13719.16, 1, '2025-12-17', 'SWEDBANK', 0),
          (4, 1, 'SELL', 13719.16, 1, '2025-12-16', 'TRADING212', 0)
        """,
      )
    }
  }

  private fun prepareUnrelatedTransactions(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        INSERT INTO portfolio_transaction VALUES
          (5, 1, 'BUY', 100, 1, '2025-12-16', 'SWEDBANK', 0),
          (6, 1, 'BUY', 13719.16, 2, '2025-12-16', 'SWEDBANK', 0),
          (7, 1, 'BUY', 13719.16, 1, '2025-12-16', 'SWEDBANK', 3),
          (8, 2, 'BUY', 13719.16, 1, '2025-12-16', 'SWEDBANK', 0),
          (9, 1, 'BUY', 13719.16, 1, '2025-12-17', 'SWEDBANK', 0),
          (10, 1, 'SELL', 13719.16, 1, '2025-12-16', 'SWEDBANK', 0),
          (11, 1, 'BUY', 13719.16, 1, '2025-11-16', 'SWEDBANK', 0),
          (12, 1, 'SELL', 13719.16, 1, '2025-11-17', 'SWEDBANK', 0),
          (13, 1, 'BUY', 13719.16, 1, '2025-12-16', 'LHV', 0),
          (14, 1, 'SELL', 13719.16, 1, '2025-12-17', 'LHV', 0)
        """,
      )
    }
  }

  private fun migrate(connection: Connection) {
    ScriptUtils.executeSqlScript(
      connection,
      ClassPathResource("db/migration/V202609242355__attribute_transfer_cash_to_trading212.sql"),
    )
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
