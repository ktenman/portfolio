package ee.tenman.portfolio.service.infrastructure

import ee.tenman.portfolio.domain.LiveUpdate
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

@Service
class LiveUpdateService {
  private val emitters = CopyOnWriteArraySet<SseEmitter>()
  private val executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("live-updates").factory())

  fun subscribe(): SseEmitter {
    val emitter = SseEmitter(Duration.ofMinutes(10).toMillis())
    emitter.onCompletion { emitters.remove(emitter) }
    emitter.onTimeout { emitter.complete() }
    emitters.add(emitter)
    executor.execute { send(emitter, SseEmitter.event().comment("connected")) }
    return emitter
  }

  fun publish(update: LiveUpdate) =
    executor.execute { emitters.forEach { send(it, SseEmitter.event().name(update.name).data(update.name)) } }

  @EventListener(ContextClosedEvent::class)
  fun close() = emitters.forEach { it.complete() }

  private fun send(
    emitter: SseEmitter,
    event: SseEmitter.SseEventBuilder,
  ) {
    runCatching { emitter.send(event) }.onFailure { emitters.remove(emitter) }
  }
}
