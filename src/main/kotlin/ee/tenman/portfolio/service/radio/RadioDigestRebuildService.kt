package ee.tenman.portfolio.service.radio

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class RadioDigestRebuildService(
  private val radioDigestService: RadioDigestService,
  private val radioDigestProgressService: RadioDigestProgressService,
  private val radioPositionService: RadioPositionService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun rebuild(show: DigestShow) {
    val date = LocalDate.now(clock)
    radioDigestProgressService.started(show, date)
    Thread.ofVirtual().name("radio-digest").start { build(show, date) }
  }

  private fun build(
    show: DigestShow,
    date: LocalDate,
  ) {
    runCatching {
      radioDigestService.rebuild(show, date)
      radioPositionService.forget(digestName(show, date))
    }.onSuccess { radioDigestProgressService.ended() }
      .onFailure {
        log.error("Rebuild of the ${show.title} digest for $date failed", it)
        radioDigestProgressService.failed(show, date, firstLine(it.message, it.javaClass.simpleName))
      }
  }
}
