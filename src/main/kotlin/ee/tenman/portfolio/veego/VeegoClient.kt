package ee.tenman.portfolio.veego

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
  name = "veegoClient",
  url = "\${veego.url:https://api.veego.ee/api}",
)
interface VeegoClient {
  @PostMapping("/vehicles/{plate}/tax")
  fun getTaxInfo(
    @PathVariable plate: String,
    @RequestBody request: VeegoTaxRequest,
  ): VeegoTaxResponse
}
