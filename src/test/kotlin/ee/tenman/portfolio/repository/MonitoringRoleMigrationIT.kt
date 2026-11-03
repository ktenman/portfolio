package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.DriverManager
import java.util.UUID
import javax.sql.DataSource

@IntegrationTest
class MonitoringRoleMigrationIT {
  @Resource
  private lateinit var dataSource: DataSource

  @Resource
  private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `leaves the monitoring login without a password when none is deployed`() {
    migrate("")
    val role =
      jdbcTemplate.queryForMap(
        "SELECT rolcanlogin, rolpassword IS NULL AS passwordless, pg_has_role('monitoring', 'pg_monitor', 'member') AS monitor " +
          "FROM pg_authid WHERE rolname = 'monitoring'",
      )
    expect(role.values.toList()).toEqual(listOf(true, true, true))
  }

  @Test
  fun `lets the monitoring login in with the deployed password`() {
    val password = "${UUID.randomUUID()}ä"
    migrate(password)
    val url = dataSource.connection.use { it.metaData.url }
    val valid = DriverManager.getConnection(url, "monitoring", password).use { it.isValid(5) }
    expect(valid).toEqual(true)
  }

  private fun migrate(password: String) {
    Flyway
      .configure()
      .dataSource(dataSource)
      .placeholders(mapOf("monitoring_password" to password))
      .load()
      .migrate()
  }
}
