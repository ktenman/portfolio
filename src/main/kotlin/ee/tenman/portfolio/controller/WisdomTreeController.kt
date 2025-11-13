package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.WisdomTreeUpdateService
import ee.tenman.portfolio.wisdomtree.WisdomTreeHolding
import ee.tenman.portfolio.wisdomtree.WisdomTreeHoldingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wisdomtree")
class WisdomTreeController(
  private val wisdomTreeHoldingsService: WisdomTreeHoldingsService,
  private val wisdomTreeUpdateService: WisdomTreeUpdateService,
) {
  @GetMapping("/holdings/{etfId}")
  fun getHoldings(
    @PathVariable etfId: String,
  ): ResponseEntity<List<WisdomTreeHolding>> {
    val holdings = wisdomTreeHoldingsService.fetchHoldings(etfId)
    return ResponseEntity.ok(holdings)
  }

  @GetMapping("/holdings")
  fun getWtaiHoldings(): ResponseEntity<List<WisdomTreeHolding>> {
    val holdings = wisdomTreeHoldingsService.fetchHoldings()
    return ResponseEntity.ok(holdings)
  }

  @PostMapping("/update-wtai")
  fun updateWtaiHoldings(): ResponseEntity<Map<String, Any>> {
    val result = wisdomTreeUpdateService.updateWtaiHoldings()
    return ResponseEntity.ok(
      mapOf<String, Any>(
        "success" to true,
        "deleted" to (result["deleted"] ?: 0),
        "created" to (result["created"] ?: 0),
        "message" to "WTAI holdings replaced with WisdomTree data",
      ),
    )
  }
}
