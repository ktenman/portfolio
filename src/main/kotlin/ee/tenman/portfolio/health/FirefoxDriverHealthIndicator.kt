package ee.tenman.portfolio.health

import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["webdriver.health.enabled"], havingValue = "true", matchIfMissing = false)
class FirefoxDriverHealthIndicator : HealthIndicator {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val healthCache = ConcurrentHashMap<String, HealthStatus>()
  private val cacheDuration = Duration.ofMinutes(5)

  data class HealthStatus(
    val health: Health,
    val timestamp: LocalDateTime,
  )

  override fun health(): Health {
    val cached = healthCache["firefox"]
    if (cached != null && cached.timestamp.isAfter(LocalDateTime.now().minus(cacheDuration))) {
      return cached.health
    }

    return try {
      val healthStatus = checkFirefoxDriver()
      healthCache["firefox"] = HealthStatus(healthStatus, LocalDateTime.now())
      healthStatus
    } catch (e: Exception) {
      logger.error("Firefox driver health check failed", e)
      val errorHealth =
        Health
          .down()
          .withDetail("error", e.message ?: "Unknown error")
          .withDetail("type", e.javaClass.simpleName)
          .build()
      healthCache["firefox"] = HealthStatus(errorHealth, LocalDateTime.now())
      errorHealth
    }
  }

  private fun checkFirefoxDriver(): Health {
    val startTime = System.currentTimeMillis()

    return try {
      val options =
        FirefoxOptions().apply {
          addArguments("--headless")
          addArguments("--no-sandbox")
          addArguments("--disable-dev-shm-usage")
          addArguments("--disable-gpu")
          addArguments("--disable-web-security")
          addArguments("--disable-features=VizDisplayCompositor")
          addArguments("--disable-extensions")
          setCapability(
            "moz:firefoxOptions",
            mapOf(
              "log" to mapOf("level" to "error"),
            ),
          )
        }

      val driver = FirefoxDriver(options)

      try {
        driver.manage().timeouts().apply {
          implicitlyWait(Duration.ofSeconds(5))
          pageLoadTimeout(Duration.ofSeconds(10))
          scriptTimeout(Duration.ofSeconds(5))
        }

        driver.get("about:blank")

        val browserInfo =
          mapOf(
            "browserName" to driver.capabilities.browserName,
            "browserVersion" to driver.capabilities.browserVersion,
            "platformName" to driver.capabilities.platformName.toString(),
            "acceptInsecureCerts" to driver.capabilities.getCapability("acceptInsecureCerts"),
            "pageLoadStrategy" to driver.capabilities.getCapability("pageLoadStrategy"),
            "unhandledPromptBehavior" to driver.capabilities.getCapability("unhandledPromptBehavior"),
          )

        val responseTime = System.currentTimeMillis() - startTime

        Health
          .up()
          .withDetail("responseTime", "$responseTime ms")
          .withDetail("browser", browserInfo)
          .withDetail("geckodriver", getGeckodriverInfo())
          .withDetail("lastChecked", LocalDateTime.now().toString())
          .build()
      } finally {
        driver.quit()
      }
    } catch (e: Exception) {
      logger.error("Failed to create Firefox driver", e)
      Health
        .down()
        .withDetail("error", e.message ?: "Failed to create Firefox driver")
        .withDetail("type", e.javaClass.simpleName)
        .withDetail("responseTime", "${System.currentTimeMillis() - startTime} ms")
        .withDetail("geckodriver", getGeckodriverInfo())
        .build()
    }
  }

  private fun getGeckodriverInfo(): Map<String, Any> =
    try {
      val process =
        ProcessBuilder("/usr/bin/geckodriver", "--version")
          .redirectErrorStream(true)
          .start()

      val output = process.inputStream.bufferedReader().use { it.readText() }
      process.waitFor(2, TimeUnit.SECONDS)

      val versionMatch = Regex("geckodriver ([0-9.]+)").find(output)
      val version = versionMatch?.groupValues?.get(1) ?: "unknown"

      mapOf(
        "version" to version,
        "path" to (System.getenv("GECKODRIVER_PATH") ?: "system PATH"),
        "available" to true,
      )
    } catch (e: Exception) {
      mapOf(
        "available" to false,
        "error" to (e.message ?: "Unknown error"),
      )
    }
}
