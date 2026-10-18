package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class RadioEpisodeService(
  private val radioOutlineService: RadioOutlineService,
  private val radioCutService: RadioCutService,
  private val radioRenderService: RadioRenderService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun make(directory: Path): Path? {
    if (!pending(directory)) return directory.resolve(EPISODE).takeIf { Files.exists(it) }
    return runCatching { render(directory) }.getOrElse { fail(directory, it) }
  }

  private fun pending(directory: Path): Boolean {
    val ready =
      listOf(EPISODE, FAILED).none { Files.exists(directory.resolve(it)) } &&
        listOf(STATUS, TRANSCRIPT_FILE).all { Files.exists(directory.resolve(it)) } &&
        readStatus(directory).state in READY
    if (!ready || Files.isWritable(directory)) return ready
    log.warn("Cannot write the radio episode into $directory")
    return false
  }

  private fun render(directory: Path): Path {
    val status = readStatus(directory)
    val lines = lines(readTranscript(directory))
    val timeline = Timeline(directory, status.parts)
    val db = loudness(timeline)
    val (reads, model) = reads(directory) { describe(status, lines, events(db)) }
    val matched = reads.filterNotNull().mapNotNull { match(it, lines) }
    check(matched.isNotEmpty()) { "No outline read in ${directory.resolve(OUTLINE)} matches the transcript" }
    val agreed = consensus(matched)
    val placed = radioCutService.boundaries(timeline, db, agreed, lines)
    val keep = placed.map { it.seconds }.chunked(2).map { it[0] to it[1] }
    val kept = audible(timeline, keep)
    val marks = chapters(kept, agreed.chapters)
    val day = Instant.ofEpochMilli((status.startedAt * 1000).toLong()).atZone(TALLINN).format(DAY)
    val temporary = directory.resolve(TEMPORARY)
    radioRenderService.render(timeline, keep, marks, "Keskpäevatund $day", temporary)
    val summary = OutlineSummary(model, reads.size, matched.size, agreed.voters)
    save(directory.resolve(EDIT_LIST), editList(status, placed, keep, kept, marks, agreed, summary))
    return Files.move(temporary, directory.resolve(EPISODE), StandardCopyOption.ATOMIC_MOVE)
  }

  private fun reads(
    directory: Path,
    text: () -> String,
  ): Pair<List<OutlineRead?>, String?> {
    val saved = directory.resolve(OUTLINE)
    if (Files.exists(saved)) return JsonMapperFactory.instance.readValue<List<OutlineRead?>>(Files.readString(saved)) to null
    val model = radioProperties.outlineModel
    val reads = radioOutlineService.reads(text(), model)
    check(reads.any { it != null }) { "No outline read came back from ${model.modelId} for $directory" }
    save(saved, reads)
    return reads to model.modelId
  }

  private fun editList(
    status: RecordingStatus,
    placed: List<Boundary>,
    keep: List<Pair<Double, Double>>,
    kept: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
    agreed: Consensus,
    summary: OutlineSummary,
  ) = EditList(
    keep = keep.map { KeptSpan(it.first, it.second) },
    durationSeconds = rounded(kept.sumOf { it.second - it.first }),
    parts = status.parts,
    boundaries = placed,
    hosts = agreed.hosts,
    outline = summary,
    chapters = marks.map { EpisodeChapter(rounded(it.first), it.second) },
    reviewed = false,
    renderedAt = now(),
  )

  private fun fail(
    directory: Path,
    failure: Throwable,
  ): Path? {
    log.error("Radio episode for $directory failed", failure)
    Files.deleteIfExists(directory.resolve(TEMPORARY))
    save(directory.resolve(FAILED), EpisodeFailure(failure.message ?: failure.javaClass.simpleName, now()))
    return null
  }

  private fun now(): String = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString()

  private fun rounded(seconds: Double): Double = BigDecimal(seconds).setScale(2, RoundingMode.HALF_EVEN).toDouble()

  private fun save(
    path: Path,
    value: Any,
  ) {
    val temporary = path.resolveSibling("${path.fileName}.tmp")
    Files.writeString(temporary, JsonMapperFactory.instance.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n")
    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  }

  companion object {
    private const val EPISODE = "episode.m4a"
    private const val TEMPORARY = "episode.tmp.m4a"
    private const val FAILED = "episode-failed.json"
    private const val STATUS = "status.json"
    private const val TRANSCRIPT_FILE = "transcript.json"
    private const val OUTLINE = "outline.json"
    private const val EDIT_LIST = "edit-list.json"
    private val READY = setOf("captured", "partial")
    private val TALLINN: ZoneId = ZoneId.of("Europe/Tallinn")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
  }
}
