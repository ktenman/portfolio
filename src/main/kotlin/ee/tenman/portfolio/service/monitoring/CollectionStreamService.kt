package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.dto.CollectionStatusDto
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

@Service
class CollectionStreamService(
  private val metrics: CollectionMetricsService,
) {
  private val emitters = CopyOnWriteArraySet<SseEmitter>()
  private val executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("collection-stream").factory())

  fun subscribe(): SseEmitter {
    val emitter = SseEmitter(0L)
    emitter.onCompletion { emitters.remove(emitter) }
    emitters.add(emitter)
    executor.execute { send(emitter, metrics.collections()) }
    return emitter
  }

  fun publish() =
    executor.execute {
      if (emitters.isEmpty()) return@execute
      metrics.refresh()
      emit()
    }

  fun broadcast() = executor.execute { if (emitters.isNotEmpty()) emit() }

  @EventListener(ContextClosedEvent::class)
  fun close() = emitters.forEach { it.complete() }

  private fun emit() {
    val collections = metrics.collections()
    emitters.forEach { send(it, collections) }
  }

  private fun send(
    emitter: SseEmitter,
    collections: List<CollectionStatusDto>,
  ) {
    runCatching { emitter.send(collections, MediaType.APPLICATION_JSON) }.onFailure { emitters.remove(emitter) }
  }
}
