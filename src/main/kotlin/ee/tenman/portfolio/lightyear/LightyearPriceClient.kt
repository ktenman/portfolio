package ee.tenman.portfolio.lightyear

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "lightyearPriceClient",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface LightyearPriceClient {
  @GetMapping("/lightyear/fetch")
  fun getPrice(
    @RequestParam path: String,
  ): LightyearPriceResponse

  @GetMapping("/lightyear/fetch")
  fun getChartData(
    @RequestParam path: String,
  ): List<LightyearChartDataPoint>

  @GetMapping("/lightyear/fetch")
  fun getHoldings(
    @RequestParam path: String,
  ): List<LightyearHoldingResponse>

  @GetMapping("/lightyear/fetch")
  fun getInstrument(
    @RequestParam path: String,
  ): LightyearInstrumentResponse

  @PostMapping("/lightyear/batch")
  fun getInstrumentBatch(
    @RequestBody instrumentIds: List<String>,
  ): List<LightyearInstrumentResponse>
}
