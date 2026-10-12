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
class DiversificationConfigMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should split a synthetic fund target between its constituents by current value`() {
    val config =
      """{"allocations":[{"instrumentId":7,"value":60},{"instrumentId":20,"value":2.5},{"instrumentId":23,"value":0.3}],"inputMode":"percentage"}"""

    val migrated = migrate(config, "(2, 'BUY', 0.05), (2, 'SELL', 0.01), (13, 'BUY', 1.2)")

    expect(migrated).toEqual(
      """{"inputMode": "percentage", "allocations": [{"value": 60, "instrumentId": 7}, {"value": 2, "instrumentId": 2}, """ +
        """{"value": 0.5, "instrumentId": 13}, {"value": 0.3, "instrumentId": 14}]}""",
    )
  }

  @Test
  fun `should split equally when no constituent is held`() {
    val migrated = migrate("""{"allocations":[{"instrumentId":20,"value":2.5}]}""", "(2, 'BUY', 0.5), (2, 'SELL', 0.5)")

    expect(migrated).toEqual("""{"allocations": [{"value": 1.25, "instrumentId": 2}, {"value": 1.25, "instrumentId": 13}]}""")
  }

  @Test
  fun `should keep the target when the rounded parts overshoot`() {
    val migrated = migrate("""{"allocations":[{"instrumentId":20,"value":0.05}]}""")

    expect(migrated).toEqual("""{"allocations": [{"value": 0.02, "instrumentId": 2}, {"value": 0.03, "instrumentId": 13}]}""")
  }

  @Test
  fun `should leave a configuration without synthetic funds untouched`() {
    val config = """{"allocations":[{"instrumentId":7,"value":100}],"inputMode":"percentage"}"""

    val migrated = migrate(config)

    expect(migrated).toEqual(config)
  }

  private fun migrate(
    config: String,
    transactions: String = "",
  ): String =
    dataSource.connection.use { connection ->
      connection.autoCommit = false
      runCatching { rewrite(connection, config, transactions) }.also { connection.rollback() }.getOrThrow()
    }

  private fun rewrite(
    connection: Connection,
    config: String,
    transactions: String,
  ): String {
    prepare(connection, transactions)
    connection.prepareStatement("INSERT INTO diversification_config VALUES (1, ?)").use { statement ->
      statement.setString(1, config)
      statement.executeUpdate()
    }
    ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V202610121714__split_synthetic_fund_allocations.sql"))
    return connection.createStatement().use { statement ->
      statement.executeQuery("SELECT config_data FROM diversification_config").use { rows ->
        rows.next()
        rows.getString(1)
      }
    }
  }

  private fun prepare(
    connection: Connection,
    transactions: String,
  ) {
    connection.createStatement().use { statement ->
      val schema = "allocation_${UUID.randomUUID().toString().replace("-", "")}"
      statement.execute("CREATE SCHEMA $schema")
      statement.execute("SET LOCAL search_path TO $schema")
      statement.execute(
        """
        CREATE TABLE instrument (id BIGINT PRIMARY KEY, symbol TEXT, provider_name TEXT, current_price NUMERIC);
        CREATE TABLE etf_holding (id BIGINT PRIMARY KEY, ticker TEXT);
        CREATE TABLE etf_position (etf_instrument_id BIGINT, holding_id BIGINT, snapshot_date DATE);
        CREATE TABLE portfolio_transaction (instrument_id BIGINT, transaction_type TEXT, quantity NUMERIC);
        CREATE TABLE diversification_config (id BIGINT PRIMARY KEY, config_data TEXT);
        INSERT INTO instrument VALUES
          (7, 'VWCE', 'LIGHTYEAR', 100), (20, 'TREZOR', 'SYNTHETIC', NULL), (23, 'CASH', 'SYNTHETIC', NULL),
          (2, 'BTCEUR', 'BINANCE', 60000), (13, 'BNBEUR', 'BINANCE', 500), (14, 'EUR', 'MANUAL', 1), (15, 'DOGEEUR', 'BINANCE', 1);
        INSERT INTO etf_holding VALUES (1, 'BTCEUR'), (2, 'BNBEUR'), (3, 'EUR'), (4, 'DOGEEUR');
        INSERT INTO etf_position VALUES
          (20, 1, '2026-08-28'), (20, 2, '2026-08-28'), (20, 4, '2026-01-01'), (23, 3, '2026-08-28')
        """,
      )
      if (transactions.isNotEmpty()) statement.execute("INSERT INTO portfolio_transaction VALUES $transactions")
    }
  }
}
