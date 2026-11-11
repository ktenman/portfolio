package ee.tenman.portfolio.poff

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
  name = "poffClient",
  url = "\${poff.url}",
)
interface PoffClient {
  @GetMapping("/en/films/")
  fun films(): String

  @GetMapping("/en/film/{slug}/")
  fun film(
    @PathVariable slug: String,
  ): String
}
