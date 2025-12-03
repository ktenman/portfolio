package ee.tenman.portfolio.configuration

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import org.springframework.test.context.support.DirtiesContextTestExecutionListener
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(TestConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@EnableWireMock(ConfigureWireMock(port = 0))
@ActiveProfiles("test")
@ContextConfiguration(initializers = [IntegrationTest.Initializer::class])
@Sql(scripts = ["/clear_database.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestExecutionListeners(
  listeners = [
    DependencyInjectionTestExecutionListener::class,
    DirtiesContextTestExecutionListener::class,
    RedisCacheCleanupListener::class,
  ],
  mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
annotation class IntegrationTest {
  companion object {
    private const val MINIO_ACCESS_KEY = "minioadmin"
    private const val MINIO_SECRET_KEY = "minioadmin"
    private const val MINIO_BUCKET_NAME = "test-portfolio-logos"

    private val POSTGRES_DB_CONTAINER: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:17-alpine")
        .apply { start() }

    private val REDIS_CONTAINER: GenericContainer<*> =
      GenericContainer(DockerImageName.parse("redis:8-alpine"))
        .withExposedPorts(6379)
        .apply { start() }

    private val MINIO_CONTAINER: MinIOContainer =
      MinIOContainer("minio/minio:latest")
        .withUserName(MINIO_ACCESS_KEY)
        .withPassword(MINIO_SECRET_KEY)
        .apply { start() }

    private fun createBucketIfNotExists(
      minioClient: MinioClient,
      bucketName: String,
    ) {
      val bucketExists =
        minioClient.bucketExists(
          BucketExistsArgs
            .builder()
            .bucket(bucketName)
            .build(),
        )

      if (!bucketExists) {
        minioClient.makeBucket(
          MakeBucketArgs
            .builder()
            .bucket(bucketName)
            .build(),
        )
      }
    }
  }

  class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
      val minioUrl = MINIO_CONTAINER.s3URL

      TestPropertyValues
        .of(
          "spring.data.redis.host=" + REDIS_CONTAINER.host,
          "spring.data.redis.port=" + REDIS_CONTAINER.firstMappedPort,
          "spring.datasource.url=" + POSTGRES_DB_CONTAINER.jdbcUrl,
          "spring.datasource.username=" + POSTGRES_DB_CONTAINER.username,
          "spring.datasource.password=" + POSTGRES_DB_CONTAINER.password,
          "minio.endpoint=$minioUrl",
          "minio.access-key=$MINIO_ACCESS_KEY",
          "minio.secret-key=$MINIO_SECRET_KEY",
          "minio.bucket-name=$MINIO_BUCKET_NAME",
        ).applyTo(applicationContext.environment)

      val minioClient =
        MinioClient
          .builder()
          .endpoint(minioUrl)
          .credentials(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)
          .build()

      createBucketIfNotExists(minioClient, MINIO_BUCKET_NAME)
    }
  }
}
