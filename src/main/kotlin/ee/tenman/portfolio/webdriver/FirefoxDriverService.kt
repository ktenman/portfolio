package ee.tenman.portfolio.webdriver

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@Service
class FirefoxDriverService {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val driverPool = ConcurrentLinkedQueue<WebDriver>()
  private val isHealthy = AtomicBoolean(false)

  @Value("\${webdriver.pool.size:2}")
  private var poolSize: Int = 2

  @Value("\${webdriver.headless:true}")
  private var headless: Boolean = true

  @Value("\${webdriver.verify.on.startup:true}")
  private var verifyOnStartup: Boolean = true

  @PostConstruct
  fun initialize() {
    if (verifyOnStartup) {
      logger.info("Verifying Firefox driver on startup...")
      val verified = verifyDriver()
      if (verified) {
        logger.info("Firefox driver verified successfully")
        isHealthy.set(true)
      } else {
        logger.error("Firefox driver verification failed - web scraping features may not work")
      }
    }
  }

  @PreDestroy
  fun cleanup() {
    logger.info("Cleaning up Firefox driver pool...")
    while (driverPool.isNotEmpty()) {
      try {
        driverPool.poll()?.quit()
      } catch (e: Exception) {
        logger.error("Error closing driver", e)
      }
    }
  }

  fun getDriver(): WebDriver? =
    try {
      var driver = driverPool.poll()
      if (driver == null || !isDriverHealthy(driver)) {
        driver?.let {
          try {
            it.quit()
          } catch (e: Exception) {
            logger.trace("Error quitting unhealthy driver", e)
          }
        }
        driver = createDriver()
      }
      driver
    } catch (e: Exception) {
      logger.error("Failed to get Firefox driver", e)
      null
    }

  fun returnDriver(driver: WebDriver) {
    try {
      if (isDriverHealthy(driver) && driverPool.size < poolSize) {
        driver.manage().deleteAllCookies()
        driver.get("about:blank")
        driverPool.offer(driver)
      } else {
        driver.quit()
      }
    } catch (e: Exception) {
      logger.error("Error returning driver to pool", e)
      try {
        driver.quit()
      } catch (ex: Exception) {
        logger.trace("Error quitting driver during cleanup", ex)
      }
    }
  }

  fun isHealthy(): Boolean = isHealthy.get()

  fun verifyDriver(): Boolean =
    try {
      val driver = createDriver()
      try {
        driver.get("about:blank")
        val title = driver.title
        logger.info("Firefox driver test successful - title: $title")
        true
      } finally {
        driver.quit()
      }
    } catch (e: Exception) {
      logger.error("Firefox driver verification failed", e)
      false
    }

  private fun createDriver(): WebDriver {
    val options = createFirefoxOptions()
    val driver = FirefoxDriver(options)

    driver.manage().timeouts().apply {
      implicitlyWait(Duration.ofSeconds(10))
      pageLoadTimeout(Duration.ofSeconds(30))
      scriptTimeout(Duration.ofSeconds(10))
    }

    driver.manage().window().setSize(org.openqa.selenium.Dimension(1920, 1080))

    return driver
  }

  private fun createFirefoxOptions(): FirefoxOptions =
    FirefoxOptions().apply {
      if (headless) {
        addArguments("--headless")
      }
      addArguments("--no-sandbox")
      addArguments("--disable-dev-shm-usage")
      addArguments("--disable-gpu")
      addArguments("--disable-web-security")
      addArguments("--disable-features=VizDisplayCompositor")
      addArguments("--disable-extensions")
      addArguments("--disable-software-rasterizer")

      addPreference("browser.download.folderList", 2)
      addPreference("browser.download.manager.showWhenStarting", false)
      addPreference("browser.helperApps.neverAsk.saveToDisk", "application/pdf,application/octet-stream")
      addPreference("pdfjs.disabled", true)

      setCapability(
        "moz:firefoxOptions",
        mapOf(
          "log" to mapOf("level" to "error"),
          "prefs" to
            mapOf(
              "dom.webdriver.enabled" to false,
              "useAutomationExtension" to false,
            ),
        ),
      )
    }

  private fun isDriverHealthy(driver: WebDriver): Boolean =
    try {
      driver.title
      true
    } catch (e: Exception) {
      logger.trace("Driver health check failed", e)
      false
    }
}
