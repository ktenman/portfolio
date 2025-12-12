package ee.tenman.portfolio.controller

import ee.tenman.portfolio.auto24.Auto24Service
import ee.tenman.portfolio.dto.CarPriceResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auto24")
class Auto24Controller(
  private val auto24Service: Auto24Service,
) {
  @GetMapping("/price")
  fun getCarPrice(
    @RequestParam regNr: String,
  ): CarPriceResponse {
    val price = auto24Service.findCarPrice(regNr)
    return CarPriceResponse(regNr = regNr, price = price)
  }
}
