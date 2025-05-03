package ee.tenman.portfolio.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/build-info")
class BuildInfoController(
  @Value("\${build.hash:unknown}") private val buildHash: String,
  @Value("\${build.time:unknown}") private val buildTime: String
) {
  @GetMapping
  fun getBuildInfo(): Map<String, String> = mapOf(
    "hash" to buildHash,
    "time" to buildTime
  )
}
