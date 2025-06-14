package ee.tenman.portfolio.alphavantage

import feign.RequestInterceptor
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.Random

@FeignClient(
  name = AlphaVantageClient.CLIENT_NAME,
  url = "\${alphavantage.url}",
  configuration = [AlphaVantageClient.Configuration::class],
)
interface AlphaVantageClient {
  companion object {
    const val CLIENT_NAME = "alphaVantageClient"
    val RANDOM = Random()
  }

  @GetMapping("/query?function={function}&symbol={symbol}&outputsize=full")
  fun getTimeSeries(
    @PathVariable function: String,
    @PathVariable symbol: String,
  ): AlphaVantageResponse

  @GetMapping("/query?function={function}&keywords={search}&page={page}")
  fun getSearch(
    @PathVariable function: String,
    @PathVariable search: String,
    @PathVariable page: String,
  ): SearchResponse

  @GetMapping("/query?function={function}&symbol={symbol}")
  fun getString(
    @PathVariable function: String,
    @PathVariable symbol: String,
  ): String

  class Configuration {
    private val simplifiedRateLimiter = SimplifiedRateLimiter()

    @Bean
    fun requestInterceptor(): RequestInterceptor {
      val keys =
        listOf(
          "LP89F1C09XFYGW1U",
          "MNIF0F2HMV93J8TX",
          "DOSJJM3U7HKZ741I",
          "WIODF0D96B7EKTK2",
          "3919KXD313S1RX7J",
          "0MOK4N559XJ9EIDR",
          "CCYG4Q2OJISVSNNI",
          "0C7DCIJ21M3HM3PU",
        )

      val randomKey = keys[RANDOM.nextInt(keys.size)]
      return RequestInterceptor { template -> simplifiedRateLimiter.execute { template.query("apikey", randomKey) } }
    }
  }
}
