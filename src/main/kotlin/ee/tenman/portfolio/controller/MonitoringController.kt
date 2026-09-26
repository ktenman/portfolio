package ee.tenman.portfolio.controller

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.dto.CollectionStatusDto
import ee.tenman.portfolio.service.monitoring.CollectionMetricsService
import ee.tenman.portfolio.service.monitoring.CollectionRerunService
import ee.tenman.portfolio.service.monitoring.CollectionStreamService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/monitoring")
class MonitoringController(
  private val collectionMetricsService: CollectionMetricsService,
  private val collectionRerunService: CollectionRerunService,
  private val collectionStreamService: CollectionStreamService,
) {
  @GetMapping("/collections")
  fun getCollections(): List<CollectionStatusDto> = collectionMetricsService.collections()

  @GetMapping("/collections/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun stream(): SseEmitter = collectionStreamService.subscribe()

  @PostMapping("/collections/{key}/rerun")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun rerun(
    @PathVariable key: CollectionKey,
  ) = collectionRerunService.rerun(key)
}
