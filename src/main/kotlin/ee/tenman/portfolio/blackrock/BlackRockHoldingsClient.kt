package ee.tenman.portfolio.blackrock

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "blackRockHoldingsClient",
  url = "\${blackrock.url:https://www.blackrock.com}",
)
interface BlackRockHoldingsClient {
  @GetMapping(
    value = [
      "/varnish-api/uk-retail01-product-data/product-data/api/v1/get-fund-document" +
        "?appType=PRODUCT_PAGE&appSubType=ONE&targetSite=uk-one&locale=en_GB&component=holdings&userType=individual",
    ],
    headers = [
      "User-Agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    ],
  )
  fun getHoldingsCsv(
    @RequestParam portfolioId: String,
  ): String
}
