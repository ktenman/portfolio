package ee.tenman.portfolio.ecb

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "ecbClient",
  url = "\${ecb.url:https://data-api.ecb.europa.eu}",
)
interface EcbClient {
  @GetMapping("/service/data/EXR/D.{currency}.EUR.SP00.A")
  fun fetchDailyRates(
    @PathVariable currency: String,
    @RequestParam startPeriod: String,
    @RequestParam format: String,
  ): String
}
