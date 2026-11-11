package ee.tenman.portfolio.controller

import ee.tenman.portfolio.domain.PoffCatalogueResponse
import ee.tenman.portfolio.service.poff.PoffCatalogueService
import ee.tenman.portfolio.service.poff.PoffStillService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/poff")
class PoffController(
  private val poffCatalogueService: PoffCatalogueService,
  private val poffStillService: PoffStillService,
) {
  @GetMapping("/catalogue")
  fun catalogue(): ResponseEntity<PoffCatalogueResponse> = ResponseEntity.ofNullable(poffCatalogueService.catalogue())

  @GetMapping("/stills/{key}")
  fun still(
    @PathVariable key: String,
    @RequestParam(defaultValue = "640") width: Int,
  ): ResponseEntity<ByteArray> =
    poffStillService.still(key, width)?.let {
      ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
        .body(it)
    } ?: ResponseEntity.notFound().build()
}
