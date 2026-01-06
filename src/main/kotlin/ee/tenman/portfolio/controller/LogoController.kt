package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.LogoCandidateDto
import ee.tenman.portfolio.dto.LogoReplacementRequest
import ee.tenman.portfolio.dto.PrefetchRequest
import ee.tenman.portfolio.service.logo.LogoCacheService
import ee.tenman.portfolio.service.logo.LogoReplacementService
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/logos")
class LogoController(
  private val logoCacheService: LogoCacheService,
  private val logoReplacementService: LogoReplacementService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @GetMapping("/{uuid}")
  fun getLogo(
    @PathVariable uuid: UUID,
  ): ResponseEntity<ByteArray> {
    log.debug("Fetching logo for holding UUID: $uuid")
    val logoData = logoCacheService.getLogo(uuid)
    if (logoData != null) {
      return ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
        .body(logoData)
    }
    log.debug("Logo not found for holding UUID: $uuid")
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
  }

  @GetMapping("/{uuid}/candidates")
  fun getLogoCandidates(
    @PathVariable uuid: UUID,
  ): ResponseEntity<List<LogoCandidateDto>> {
    log.debug("Fetching logo candidates for holding UUID: $uuid")
    val candidates = logoReplacementService.getCandidates(uuid)
    return ResponseEntity.ok(candidates)
  }

  @GetMapping("/search")
  fun searchLogoCandidates(
    @org.springframework.web.bind.annotation.RequestParam name: String,
  ): ResponseEntity<List<LogoCandidateDto>> {
    log.debug("Searching logo candidates for name: $name")
    val candidates = logoReplacementService.searchByName(name)
    return ResponseEntity.ok(candidates)
  }

  @PostMapping("/replace")
  fun replaceLogo(
    @RequestBody request: LogoReplacementRequest,
  ): ResponseEntity<Map<String, Any>> {
    log.info("Replacing logo for holding UUID: ${request.holdingUuid} with candidate index: ${request.candidateIndex}")
    val success = logoReplacementService.replaceLogo(request.holdingUuid, request.candidateIndex)
    return if (success) {
      ResponseEntity.ok(mapOf("success" to true, "message" to "Logo replaced successfully"))
    } else {
      ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to replace logo"))
    }
  }

  @PostMapping("/prefetch")
  fun prefetchCandidates(
    @RequestBody request: PrefetchRequest,
  ): ResponseEntity<Map<String, Any>> {
    log.info("Prefetching logo candidates for ${request.holdingUuids.size} holdings")
    Thread.startVirtualThread { logoReplacementService.prefetchCandidates(request.holdingUuids) }
    return ResponseEntity.ok(mapOf("success" to true, "message" to "Prefetch started"))
  }
}
