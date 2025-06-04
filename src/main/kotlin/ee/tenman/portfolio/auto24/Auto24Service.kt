package ee.tenman.portfolio.auto24

import com.codeborne.selenide.Condition
import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.WebDriverRunner
import org.openqa.selenium.By
import org.openqa.selenium.Cookie
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.io.File
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.random.Random

@Service
class Auto24Service(private val captchaService: CaptchaService) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MAX_ATTEMPTS = 10
    private const val RETRY_DELAY_MS = 2000L
    private const val AUTO24_BASE_URL = "https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring&vpc_reg_nr=463bkh&checksec1=387c&vpc_reg_search=1"
    private const val COOKIES_FILE = "/tmp/auto24_cookies.txt"
  }

  /**
   * Creates Firefox options with comprehensive anti-detection measures
   * This configuration makes the browser appear more like a regular user's browser
   */
  private fun createFirefoxOptions(): FirefoxOptions {
    val options = FirefoxOptions()

    // Comprehensive preferences to avoid detection
    val prefs = mapOf(
      // Basic download preferences
      "browser.download.folderList" to 2,
      "browser.download.manager.showWhenStarting" to false,

      // User agent spoofing - using a common Windows Firefox version
      "general.useragent.override" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0",

      // Critical anti-detection preferences
      "dom.webdriver.enabled" to false,  // Hides webdriver property
      "useAutomationExtension" to false,
      "dom.webnotifications.enabled" to false,
      "media.navigator.enabled" to false,
      "media.peerconnection.enabled" to false,
      "services.sync.prefs.sync.browser.download.manager.showWhenStarting" to false,

      // Additional privacy and fingerprinting resistance
      "privacy.resistFingerprinting" to false,  // Counterintuitively, this can make you more detectable
      "permissions.default.image" to 1,  // Allow images
      "permissions.default.stylesheet" to 1,  // Allow CSS
      "dom.ipc.plugins.enabled" to true,
      "network.IDN_show_punycode" to false,

      // Canvas and WebGL settings
      "webgl.disabled" to false,
      "webgl.vendor" to "Intel Inc.",
      "webgl.renderer" to "Intel Iris OpenGL Engine",

      // Language and locale settings
      "intl.accept_languages" to "en-US,en;q=0.9",
      "javascript.options.showInConsole" to false,

      // Disable various APIs that can be used for fingerprinting
      "geo.enabled" to false,
      "dom.battery.enabled" to false,
      "dom.event.clipboardevents.enabled" to true
    )

    prefs.forEach { (key, value) ->
      options.addPreference(key, value)
    }

    // Comprehensive command line arguments
    val arguments = listOf(
      "--disable-blink-features=AutomationControlled",  // Critical for avoiding detection
      "--no-sandbox",
      "--disable-dev-shm-usage",
      "--disable-setuid-sandbox",
      "--disable-accelerated-2d-canvas",
      "--disable-gpu",
      "--window-size=1920,1080",  // Common resolution
      "--start-maximized",
      "--user-data-dir=/tmp/firefox-profile-${UUID.randomUUID()}",  // Unique profile each time
      "--hide-scrollbars",
      "--mute-audio",
      "--disable-features=VizDisplayCompositor",
      "--disable-background-timer-throttling",
      "--disable-renderer-backgrounding",
      "--disable-features=TranslateUI",
      "--disable-ipc-flooding-protection",
      "--password-store=basic",
      "--use-mock-keychain",
      "--force-color-profile=srgb"
    )

    options.addArguments(arguments)

    // Configure Selenide settings
    Configuration.browser = "firefox"
    Configuration.browserCapabilities = options
    Configuration.browserSize = "1920x1080"
    Configuration.timeout = 15000
    Configuration.pageLoadTimeout = 30000
    Configuration.headless = true

    return options
  }

  /**
   * Main entry point - finds car price with full anti-detection measures
   */
  @Retryable(backoff = Backoff(delay = 1000))
  fun findCarPrice(regNr: String): String {
    try {
      // Initialize browser with anti-detection settings
      createFirefoxOptions()

      // Open the page and handle initial setup
      openPageWithAntiDetection(regNr)

      // Wait for any Cloudflare challenges to complete
      if (waitForCloudflareChallenge()) {
        log.info("Successfully passed Cloudflare challenge")
      }

      // Apply additional anti-detection JavaScript
      removeWebDriverProperties()

      // Handle cookies with human-like behavior
      handleCookiesWithDelay()

      // Fill form and solve captcha
      fillFormWithHumanBehavior(regNr)
      solveCaptcha(regNr)

      // Extract and return the price
      return extractCarPrice()

    } catch (e: Exception) {
      log.error("Failed to get car price", e)
      debugPageState()  // Log current state for debugging
      throw e
    } finally {
      saveSessionCookies()  // Save cookies for potential reuse
    }
  }

  /**
   * Opens the page with various anti-detection measures
   */
  private fun openPageWithAntiDetection(regNr: String) {
    // Try to load existing cookies first
    loadSessionCookies()

    // Navigate to the page
    Selenide.open(AUTO24_BASE_URL)

    // Add random mouse movements immediately after page load
    performRandomMouseMovements()

    // Natural delay before any actions
    humanDelay(1000, 2000)
  }

  /**
   * Waits for Cloudflare challenge to complete
   * Returns true if successfully passed, false if timeout
   */
  private fun waitForCloudflareChallenge(): Boolean {
    return try {
      val wait = WebDriverWait(WebDriverRunner.getWebDriver(), Duration.ofSeconds(30))

      wait.until { driver ->
        val pageSource = driver.pageSource
        val currentUrl = driver.currentUrl

        // Check multiple indicators that we're past Cloudflare
        // Using safe navigation (?.) to handle potential null values
        val passedCloudflare = pageSource?.let { source ->
          !source.contains("Cloudflare") &&
            !source.contains("cf-challenge") &&
            !source.contains("Palun lahendage turvaküsimus") &&
            !source.contains("Checking your browser") &&
            !source.contains("This process is automatic") &&
            currentUrl?.contains("auto24.ee") == true
        } ?: false  // If pageSource is null, default to false

        // Also check if our target form exists
        val hasTargetForm = driver.findElements(By.name("vpc_reg_nr")).isNotEmpty()

        passedCloudflare || hasTargetForm
      }

      true
    } catch (e: TimeoutException) {
      log.warn("Cloudflare challenge timeout - attempting to continue anyway")
      false
    }
  }

  /**
   * Injects JavaScript to remove automation detection properties
   * This is crucial for bypassing many detection mechanisms
   */
  private fun removeWebDriverProperties() {
    val js = WebDriverRunner.getWebDriver() as JavascriptExecutor

    // Comprehensive JavaScript to hide automation
    js.executeScript("""
            // Remove webdriver property
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined
            });

            // Remove automation indicators in Chrome (even though we're using Firefox)
            window.navigator.chrome = {
                runtime: {},
            };

            // Override permissions API
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => (
                parameters.name === 'notifications' ?
                    Promise.resolve({ state: Notification.permission }) :
                    originalQuery(parameters)
            );

            // Fix navigator properties
            Object.defineProperty(navigator, 'plugins', {
                get: () => {
                    // Return a realistic plugins array
                    return [
                        { name: 'PDF Viewer', filename: 'internal-pdf-viewer' },
                        { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer' },
                        { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai' },
                        { name: 'Native Client', filename: 'internal-nacl-plugin' }
                    ];
                }
            });

            // Fix languages
            Object.defineProperty(navigator, 'languages', {
                get: () => ['en-US', 'en']
            });

            // Fix platform
            Object.defineProperty(navigator, 'platform', {
                get: () => 'Win32'
            });

            // Override hardwareConcurrency
            Object.defineProperty(navigator, 'hardwareConcurrency', {
                get: () => 8
            });

            // Override connection info
            Object.defineProperty(navigator, 'connection', {
                get: () => ({
                    effectiveType: '4g',
                    rtt: 50,
                    downlink: 10.0,
                    saveData: false
                })
            });

            // Fix WebGL vendor and renderer
            const getParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
                if (parameter === 37445) {
                    return 'Intel Inc.';
                }
                if (parameter === 37446) {
                    return 'Intel Iris OpenGL Engine';
                }
                return getParameter.apply(this, arguments);
            };

            // Remove Selenium-specific properties
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
        """.trimIndent())
  }

  /**
   * Simulates human-like delay with random variation
   */
  private fun humanDelay(minMs: Long = 500, maxMs: Long = 2000) {
    val delay = Random.nextLong(minMs, maxMs)
    TimeUnit.MILLISECONDS.sleep(delay)
  }

  /**
   * Performs random mouse movements to simulate human behavior
   */
  private fun performRandomMouseMovements() {
    try {
      val actions = Actions(WebDriverRunner.getWebDriver())

      // Create a curved path with multiple points
      val steps = Random.nextInt(3, 7)

      for (i in 1..steps) {
        // Generate random but reasonable coordinates
        val x = Random.nextInt(-200, 200)
        val y = Random.nextInt(-200, 200)

        actions.moveByOffset(x, y).perform()
        humanDelay(50, 150)
      }

      // Move to center of screen
      actions.moveByOffset(-actions.build().toString().length / 2, 0).perform()

    } catch (e: Exception) {
      log.debug("Mouse movement simulation failed: ${e.message}")
    }
  }

  /**
   * Handles cookie consent with human-like behavior
   */
  private fun handleCookiesWithDelay() {
    humanDelay(800, 1500)

    val acceptCookies = Selenide.elements(By.tagName("button"))
      .find(Condition.text("Nõustun"))

    if (acceptCookies.exists()) {
      // Move mouse to button before clicking
      val actions = Actions(WebDriverRunner.getWebDriver())
      actions.moveToElement(acceptCookies).perform()
      humanDelay(200, 500)

      acceptCookies.click()
      humanDelay(500, 1000)
    }
  }

  /**
   * Fills the form with human-like typing behavior
   */
  private fun fillFormWithHumanBehavior(regNr: String) {
    // Wait for form to be available
    val regNrField = Selenide.element(By.name("vpc_reg_nr"))
      .shouldBe(Condition.visible)

    // Click on the field first
    regNrField.click()
    humanDelay(100, 300)

    // Clear any existing value
    regNrField.clear()
    humanDelay(100, 200)

    // Type each character with random delay
    regNr.forEach { char ->
      regNrField.sendKeys(char.toString())
      humanDelay(50, 150)  // Simulate typing speed
    }

    humanDelay(300, 700)

    // Find and click submit button
    val submitButton = Selenide.element(By.className("sbmt"))

    // Move mouse to button before clicking
    val actions = Actions(WebDriverRunner.getWebDriver())
    actions.moveToElement(submitButton).perform()
    humanDelay(200, 400)

    submitButton.click()
  }

  /**
   * Original captcha solving logic with added delays
   */
  private fun solveCaptcha(regNr: String) {
    repeat(MAX_ATTEMPTS) { attempt ->
      log.info("CAPTCHA attempt ${attempt + 1} of $MAX_ATTEMPTS")

      humanDelay(1000, 2000)  // Wait for captcha to load

      if (processCaptchaAttempt()) return

      if (Selenide.element(".vpc_error_msg").exists()) {
        log.info("Error message found, retrying...")

        // Re-enter registration number with human behavior
        fillFormWithHumanBehavior(regNr)
        humanDelay(RETRY_DELAY_MS - 500, RETRY_DELAY_MS + 500)
      }
    }
    throw RuntimeException("Failed to solve CAPTCHA after $MAX_ATTEMPTS attempts")
  }

  /**
   * Processes a single captcha attempt
   */
  private fun processCaptchaAttempt(): Boolean {
    humanDelay(1000, 1500)

    // Check if captcha exists
    val captchaElement = Selenide.element(By.id("vpc_captcha"))
    if (!captchaElement.exists()) {
      log.info("No captcha found - possibly already solved")
      return true
    }

    val screenshot = captchaElement.screenshot() ?: return false

    val imageFile = screenshot.toPath().toFile()
    val imageBytes = imageFile.readBytes()
    val image = ImageIO.read(imageFile)

    log.info(
      "Captcha image details - Size: ${imageBytes.size} bytes, " +
        "Width: ${image.width}px, " +
        "Height: ${image.height}px"
    )

    val base64Image = Base64.getEncoder().encodeToString(imageBytes)
    val response = captchaService.predict(PredictionRequest(UUID.randomUUID(), base64Image))

    log.info("Captcha prediction: ${response.prediction}")
    log.info("Confidence: ${response.confidence}")
    log.info("Processing time: ${response.processingTimeMs}ms")

    // Type captcha solution with human-like behavior
    val captchaField = Selenide.element(By.name("checksec1"))
    captchaField.click()
    humanDelay(100, 300)

    // Type each character
    response.prediction.forEach { char ->
      captchaField.sendKeys(char.toString())
      humanDelay(50, 150)
    }

    humanDelay(300, 700)

    // Submit
    Selenide.element(By.className("sbmt")).click()
    humanDelay(1000, 2000)

    return !Selenide.element(By.id("vpc_captcha")).exists()
  }

  /**
   * Extracts the car price from the results page
   */
  private fun extractCarPrice(): String {
    humanDelay(1000, 2000)

    return try {
      val priceElement = Selenide.element(By.className("result"))
        .findAll(By.className("label"))
        .last()
        .parent()
        .find(By.tagName("b"))

      if (priceElement.exists()) {
        priceElement.text
      } else {
        "Price not found"
      }
    } catch (e: Exception) {
      log.error("Failed to extract car price", e)
      "Error extracting price"
    }
  }

  /**
   * Saves session cookies for potential reuse
   */
  private fun saveSessionCookies() {
    try {
      val cookies = WebDriverRunner.getWebDriver().manage().cookies
      val cookieData = cookies.joinToString("\n") { cookie ->
        "${cookie.name}=${cookie.value};${cookie.domain};${cookie.path};${cookie.expiry};${cookie.isSecure}"
      }
      File(COOKIES_FILE).writeText(cookieData)
      log.debug("Saved ${cookies.size} cookies")
    } catch (e: Exception) {
      log.debug("Failed to save cookies: ${e.message}")
    }
  }

  /**
   * Loads previously saved session cookies
   */
  private fun loadSessionCookies() {
    try {
      val cookieFile = File(COOKIES_FILE)
      if (cookieFile.exists()) {
        val driver = WebDriverRunner.getWebDriver()
        cookieFile.readLines().forEach { line ->
          val parts = line.split(";")
          if (parts.size >= 5) {
            val cookie = Cookie(parts[0].substringBefore("="),
              parts[0].substringAfter("="))
            driver.manage().addCookie(cookie)
          }
        }
        log.debug("Loaded cookies from previous session")
      }
    } catch (e: Exception) {
      log.debug("Failed to load cookies: ${e.message}")
    }
  }

  /**
   * Debug helper to understand current page state
   */
  private fun debugPageState() {
    try {
      val driver = WebDriverRunner.getWebDriver()

      // Take screenshot
      val screenshot = Selenide.screenshot("debug-${System.currentTimeMillis()}")
      log.info("Debug screenshot saved: $screenshot")

      // Log current state
      log.info("Current URL: ${driver.currentUrl}")
      log.info("Page title: ${driver.title}")

      // Check for various elements
      log.info("Has Cloudflare elements: ${elements(".cf-challenge").size() > 0}")
      log.info("Has target form: ${elements(By.name("vpc_reg_nr")).size() > 0}")
      log.info("Has captcha: ${elements(By.id("vpc_captcha")).size() > 0}")

      // Save page source
      val pageSource = driver.pageSource

      // Log first 500 chars of page source
      log.info("Page source preview: ${pageSource?.take(500)}...")

    } catch (e: Exception) {
      log.error("Failed to capture debug state", e)
    }
  }
}
