package ee.tenman.portfolio.tuleva

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal
import java.time.LocalDate

@FeignClient(
  name = "tulevaNavClient",
  url = "\${tuleva.url}",
)
interface TulevaNavClient {
  @GetMapping("/v1/funds/{isin}/nav")
  fun getNav(
    @PathVariable isin: String,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
  ): List<TulevaNav>
}

data class TulevaNav(
  val date: LocalDate,
  val value: BigDecimal,
)

fun List<TulevaNav>.toDailyPrices(): Map<LocalDate, DailyPriceData> =
  associate { it.date to DailyPriceDataImpl(open = it.value, high = it.value, low = it.value, close = it.value, volume = 0) }
