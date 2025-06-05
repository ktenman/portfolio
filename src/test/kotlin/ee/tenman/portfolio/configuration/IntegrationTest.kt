package ee.tenman.portfolio.configuration

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import org.springframework.test.context.support.DirtiesContextTestExecutionListener
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@ContextConfiguration(initializers = [IntegrationTest.Initializer::class])
@Sql(scripts = ["/clear_database.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestExecutionListeners(
    listeners = [
        DependencyInjectionTestExecutionListener::class,
        DirtiesContextTestExecutionListener::class,
        RedisCacheCleanupListener::class
    ],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
annotation class IntegrationTest {
  companion object {
    private val POSTGRES_DB_CONTAINER: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:17-alpine")
        .apply { start() }

    private val REDIS_CONTAINER: GenericContainer<*> =
      GenericContainer(DockerImageName.parse("redis:8-alpine"))
        .withExposedPorts(6379)
        .apply { start() }
  }

  class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
      TestPropertyValues.of(
        "spring.data.redis.host=" + REDIS_CONTAINER.host,
        "spring.data.redis.port=" + REDIS_CONTAINER.firstMappedPort,

        "spring.datasource.url=" + POSTGRES_DB_CONTAINER.jdbcUrl,
        "spring.datasource.username=" + POSTGRES_DB_CONTAINER.username,
        "spring.datasource.password=" + POSTGRES_DB_CONTAINER.password
      ).applyTo(applicationContext.environment)
    }
  }

}
