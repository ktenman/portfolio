package ee.tenman.portfolio.trading212

import feign.RequestInterceptor
import feign.auth.BasicAuthRequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "trading212Client",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface Trading212Client {
  @GetMapping("/prices")
  fun getPrices(
    @RequestParam tickers: String,
  ): Trading212Response
}

@FeignClient(
  name = "trading212EtfClient",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface Trading212EtfClient {
  @GetMapping("/trading212/etf-holdings")
  fun getHoldings(
    @RequestParam ticker: String,
  ): List<Trading212EtfHolding>

  @GetMapping("/trading212/etf-summary")
  fun getSummary(
    @RequestParam ticker: String,
  ): Trading212EtfSummary
}

@FeignClient(
  name = "trading212CatalogueClient",
  url = "\${trading212.api.base-url:https://live.trading212.com}",
  configuration = [Trading212CatalogueConfig::class],
)
interface Trading212CatalogueClient {
  @GetMapping("/api/v0/equity/metadata/instruments")
  fun fetchInstruments(): List<Trading212Instrument>
}

class Trading212CatalogueConfig {
  @Bean
  fun basicAuthInterceptor(
    @Value("\${trading212.api.key-id:}") keyId: String,
    @Value("\${trading212.api.key-secret:}") keySecret: String,
  ): RequestInterceptor {
    if (keyId.isBlank() || keySecret.isBlank()) return RequestInterceptor { }
    return BasicAuthRequestInterceptor(keyId, keySecret)
  }
}
