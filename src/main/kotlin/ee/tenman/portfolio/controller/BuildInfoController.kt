package ee.tenman.portfolio.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/build-info")
class BuildInfoController(
  @Value("\${build.hash:local}") private val buildHash: String,
  @Value("\${build.time:unknown}") private val buildTime: String
) {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  @GetMapping
  fun getBuildInfo(): Map<String, String> {
    // If buildTime is empty, use current time
    val time = if ("unknown" == buildTime) {
      LocalDateTime.now().format(dateTimeFormatter)
    } else {
      buildTime
    }

    return mapOf(
      "hash" to buildHash,
      "time" to time
    )
  }
}
