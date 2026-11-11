package ee.tenman.portfolio.controller

import ee.tenman.portfolio.domain.PoffCatalogueResponse
import ee.tenman.portfolio.service.poff.PoffCatalogueService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/poff")
class PoffController(
  private val poffCatalogueService: PoffCatalogueService,
) {
  @GetMapping("/catalogue")
  fun catalogue(): ResponseEntity<PoffCatalogueResponse> = ResponseEntity.ofNullable(poffCatalogueService.catalogue())
}
