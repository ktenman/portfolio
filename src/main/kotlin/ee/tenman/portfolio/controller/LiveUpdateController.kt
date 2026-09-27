package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/live-updates")
class LiveUpdateController(
  private val liveUpdateService: LiveUpdateService,
) {
  @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun stream(): SseEmitter = liveUpdateService.subscribe()
}
