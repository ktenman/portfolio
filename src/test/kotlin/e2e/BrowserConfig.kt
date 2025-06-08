package e2e

import com.codeborne.selenide.Configuration

object BrowserConfig {
  fun configureBrowser() {
    Configuration.browser = "firefox"
    Configuration.browserSize = "1920x1080"
    Configuration.timeout = 20000
    Configuration.headless = true
    Configuration.screenshots = true
    Configuration.savePageSource = true
//    Configuration.reportsFolder = "target/selenide-reports"
    Configuration.fastSetValue = true // Faster input for form fields
    Configuration.pageLoadTimeout = 30000
    Configuration.browserTimeout = 30000
  }
}
