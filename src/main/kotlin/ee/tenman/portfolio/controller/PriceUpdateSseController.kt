package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.PriceUpdateEventService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/price-updates")
@Tag(name = "Price Updates", description = "Server-Sent Events for real-time price updates")
class PriceUpdateSseController(
  private val priceUpdateEventService: PriceUpdateEventService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  @Operation(summary = "Subscribe to real-time price updates via Server-Sent Events")
  fun streamPriceUpdates(): SseEmitter {
    val emitter = SseEmitter(0L)
    priceUpdateEventService.addEmitter(emitter)

    emitter.onCompletion {
      log.info("SSE emitter completed")
      priceUpdateEventService.removeEmitter(emitter)
    }

    emitter.onTimeout {
      log.info("SSE emitter timed out")
      priceUpdateEventService.removeEmitter(emitter)
    }

    emitter.onError {
      log.error("SSE emitter error", it)
      priceUpdateEventService.removeEmitter(emitter)
    }

    log.info("New SSE client connected. Total clients: ${priceUpdateEventService.getEmitterCount()}")
    return emitter
  }
}
