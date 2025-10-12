package ee.tenman.portfolio.service

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tenman.portfolio.dto.PriceUpdateEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@Service
class PriceUpdateEventService(
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val emitters = CopyOnWriteArrayList<SseEmitter>()

  fun addEmitter(emitter: SseEmitter) {
    emitters.add(emitter)
    log.info("Added SSE emitter. Total: {}", emitters.size)
  }

  fun removeEmitter(emitter: SseEmitter) {
    emitters.remove(emitter)
    log.info("Removed SSE emitter. Total: {}", emitters.size)
  }

  fun getEmitterCount(): Int = emitters.size

  fun broadcastPriceUpdate(
    type: String,
    message: String,
    updatedCount: Int,
  ) {
    if (emitters.isEmpty()) {
      log.debug("No SSE clients connected, skipping broadcast")
      return
    }

    val event =
      PriceUpdateEvent(
        type = type,
        message = message,
        updatedCount = updatedCount,
      )

    val deadEmitters = mutableListOf<SseEmitter>()
    val sanitizedMessage = sanitizeForLogging(message)

    emitters.forEach { emitter ->
      try {
        val eventData = objectMapper.writeValueAsString(event)
        emitter.send(
          SseEmitter
            .event()
            .name("price-update")
            .data(eventData),
        )
        log.debug("Sent price update event to client: {}", sanitizedMessage)
      } catch (e: IOException) {
        log.warn("Failed to send SSE event, marking emitter for removal", e)
        deadEmitters.add(emitter)
      } catch (e: Exception) {
        log.error("Unexpected error sending SSE event", e)
        deadEmitters.add(emitter)
      }
    }

    deadEmitters.forEach { removeEmitter(it) }

    log.info("Broadcasted price update to {} clients: {}", emitters.size, sanitizedMessage)
  }

  private fun sanitizeForLogging(input: String): String = input.replace("\n", " ").replace("\r", " ")
}
