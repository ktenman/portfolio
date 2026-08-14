package e2e

import com.codeborne.selenide.Configuration

object BrowserConfig {
  val baseUrl: String = System.getenv("E2E_BASE_URL") ?: "http://localhost:61234"

  fun configureBrowser() {
    Configuration.browser = "firefox"
    Configuration.browserSize = "1920x1080"
    Configuration.timeout = 10000
    Configuration.headless = true
    Configuration.screenshots = true
    Configuration.savePageSource = true
    Configuration.fastSetValue = true
  }
}
