package ee.tenman.portfolio.trading212

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping

@FeignClient(
  name = "trading212CatalogueClient",
  url = "\${trading212.api.base-url:https://live.trading212.com}",
  configuration = [Trading212CatalogueConfig::class],
)
interface Trading212CatalogueClient {
  @GetMapping("/api/v0/equity/metadata/instruments")
  fun fetchInstruments(): List<Trading212Instrument>
}
