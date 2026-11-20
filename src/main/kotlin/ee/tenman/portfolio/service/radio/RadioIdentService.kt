package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.Station
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock

@Service
class RadioIdentService(
  private val radioRenderService: RadioRenderService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun save(
    status: RecordingStatus,
    timeline: Timeline,
    boundaries: List<Boundary>,
    words: List<TranscriptWord>,
  ) {
    runCatching { identCuts(timeline, boundaries, words).forEach { store(status.station, timeline, it) } }
      .onFailure { log.warn("Cannot save the jingle of ${status.station}", it) }
  }

  private fun store(
    station: Station,
    timeline: Timeline,
    cut: IdentCut,
  ) {
    val span = timeline.spans.first { cut.start >= it.start && cut.start < it.end }
    val directory = Files.createDirectories(radioProperties.idents)
    val file = "${station.name.lowercase()}-${safe(fold(cut.programme))}.m4a"
    val temporary = directory.resolve("tmp-$file")
    radioRenderService.ident(span.audio, cut.start - span.offset, cut.seconds, temporary)
    Files.move(temporary, directory.resolve(file), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    val kept = idents(directory).filterNot { it.file == file }
    save(directory.resolve(IDENTS_FILE), kept + Ident(station, cut.programme, file, cut.seconds, clock.instant().toString()))
  }
}
