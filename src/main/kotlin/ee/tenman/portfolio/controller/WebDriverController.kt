package ee.tenman.portfolio.controller

import ee.tenman.portfolio.webdriver.FirefoxDriverService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/webdriver")
class WebDriverController(
    private val firefoxDriverService: FirefoxDriverService,
) {
  @GetMapping("/firefox/verify")
    fun verifyFirefoxDriver(): WebDriverVerificationResponse {
        val startTime = System.currentTimeMillis()
        val isHealthy = firefoxDriverService.verifyDriver()
        val duration = System.currentTimeMillis() - startTime

        return WebDriverVerificationResponse(
            driver = "Firefox",
            healthy = isHealthy,
            message = if (isHealthy) "Firefox driver is working properly" else "Firefox driver verification failed",
            durationMs = duration,
            timestamp = LocalDateTime.now(),
        )
    }

    @GetMapping("/firefox/status")
    fun getFirefoxDriverStatus(): WebDriverStatusResponse =
      WebDriverStatusResponse(
            driver = "Firefox",
            poolHealthy = firefoxDriverService.isHealthy(),
            timestamp = LocalDateTime.now(),
        )
}

data class WebDriverVerificationResponse(
    val driver: String,
    val healthy: Boolean,
    val message: String,
    val durationMs: Long,
    val timestamp: LocalDateTime,
)

data class WebDriverStatusResponse(
    val driver: String,
    val poolHealthy: Boolean,
    val timestamp: LocalDateTime,
)
