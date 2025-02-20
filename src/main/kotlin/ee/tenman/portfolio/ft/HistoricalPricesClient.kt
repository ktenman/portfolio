package ee.tenman.portfolio.ft

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "historicalPricesClient", url = "https://markets.ft.com")
interface HistoricalPricesClient {

  @GetMapping("/data/equities/ajax/get-historical-prices")
  fun getHistoricalPrices(
    @RequestParam("startDate") startDate: String,
    @RequestParam("endDate") endDate: String,
    @RequestParam("symbol") symbol: String
  ): HistoricalPricesResponse
}
