package ee.tenman.portfolio.openfigi

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(name = "openFigiClient", url = "\${openfigi.url:https://api.openfigi.com}")
interface OpenFigiClient {
  @PostMapping("/v3/mapping", consumes = ["application/json"])
  fun map(
    @RequestBody queries: List<OpenFigiQuery>,
  ): List<OpenFigiEntry>
}
