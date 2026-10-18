package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.kuku.KukuEpisode
import ee.tenman.portfolio.kuku.KukuShow
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.time.Clock
import java.time.Instant

@Service
class RadioScheduleService(
  private val radioProgrammeRepository: RadioProgrammeRepository,
  private val radioAiringRepository: RadioAiringRepository,
  private val radioArchiveService: RadioArchiveService,
  private val radioProperties: RadioProperties,
  private val clock: Clock,
) {
  @Transactional
  fun replace(
    episodes: List<KukuEpisode>,
    from: Instant,
    until: Instant,
  ) {
    val shown = episodes.mapNotNull { episode -> episode.show?.let { episode to it } }
    check(shown.isNotEmpty()) { "Kuku schedule from $from to $until lists no programmes" }
    store(shown, upsert(shown.map { it.second }.distinctBy { it.id }))
    radioAiringRepository.deleteDropped(from, until, shown.map { it.first.id })
  }

  fun programmes(): List<RadioProgrammeDto> {
    val upcoming = radioAiringRepository.findByEndsAtAfterOrderByStartsAt(clock.instant()).groupBy { it.programmeId }
    val archived =
      radioArchiveService
      .statuses()
      .flatMap { it.programmes }
      .groupingBy { it }
      .eachCount()
    return radioProgrammeRepository
      .findAll()
      .filter { it.recordEvery || it.id in upcoming }
      .sortedWith(compareBy({ !it.recordEvery }, { upcoming[it.id]?.first()?.startsAt ?: Instant.MAX }, { it.name }))
      .map { dto(it, upcoming[it.id].orEmpty(), archived[it.name] ?: 0) }
  }

  fun choose(
    id: Long,
    choice: ProgrammeChoice,
  ): Boolean {
    val programme = radioProgrammeRepository.findByKukuId(id) ?: return false
    programme.recordEvery = choice.recordEvery
    radioProgrammeRepository.save(programme)
    plan()
    return true
  }

  fun plan() {
    val root = radioProperties.recordings
    if (!Files.isDirectory(root)) return
    save(root.resolve(PLAN_FILE), planned(radioAiringRepository.findByEndsAtAfterOrderByStartsAt(clock.instant())))
  }

  fun station(): RadioStation {
    val now = clock.instant()
    val airings = radioAiringRepository.findByEndsAtAfterOrderByStartsAt(now)
    val captures = planned(airings)
    val onAir = airings.firstOrNull { it.startsAt <= now }
    val next = airings.firstOrNull { it.startsAt > now }
    val names = radioProgrammeRepository.findAllById(listOfNotNull(onAir, next).map { it.programmeId }).associate { it.id to it.name }
    val root = radioProperties.recordings
    val free = root.takeIf(Files::isDirectory)?.let { Files.getFileStore(it).usableSpace }
    return RadioStation(
      onAir = onAir?.let { live(it, names, captures) },
      next = next?.let { live(it, names, captures) },
      capture = captures.firstOrNull(),
      freeBytes = free,
      hours = free?.div(BYTES_PER_HOUR),
    )
  }

  private fun planned(airings: List<RadioAiring>): List<Capture> {
    val chosen = radioProgrammeRepository.findByRecordEveryTrue().associateBy { it.id }
    val planned =
      airings
        .filter { it.originalId == null }
        .mapNotNull { airing -> chosen[airing.programmeId]?.let { PlannedAiring(it.name, it.slug, airing.startsAt, airing.endsAt) } }
    return captures(planned, clock.zone)
  }

  private fun live(
    airing: RadioAiring,
    names: Map<Long, String>,
    captures: List<Capture>,
  ): StationAiring {
    val start = airing.startsAt.epochSecond
    val end = airing.endsAt.epochSecond
    return StationAiring(names.getValue(airing.programmeId), start, end, captures.any { it.start <= start && it.end >= end })
  }

  private fun upsert(shows: List<KukuShow>): Map<Long, Long> {
    val existing = radioProgrammeRepository.findAllByKukuIdIn(shows.map { it.id }).associateBy { it.kukuId }
    val saved =
      shows.map { show ->
        (existing[show.id] ?: RadioProgramme(show.id, show.name, show.slug)).apply {
          name = show.name
          slug = show.slug
          description = show.description
        }
      }
    return radioProgrammeRepository.saveAll(saved).associate { it.kukuId to it.id }
  }

  private fun store(
    shown: List<Pair<KukuEpisode, KukuShow>>,
    programmes: Map<Long, Long>,
  ) {
    val existing = radioAiringRepository.findAllByKukuIdIn(shown.map { it.first.id }).associateBy { it.kukuId }
    radioAiringRepository.saveAll(shown.map { (episode, show) -> airing(existing[episode.id], episode, programmes.getValue(show.id)) })
  }

  private fun airing(
    existing: RadioAiring?,
    episode: KukuEpisode,
    programmeId: Long,
  ): RadioAiring =
    (existing ?: RadioAiring(episode.id, programmeId, episode.title, episode.start.toInstant(), episode.end.toInstant())).apply {
      this.programmeId = programmeId
      title = episode.title
      startsAt = episode.start.toInstant()
      endsAt = episode.end.toInstant()
      originalId = episode.originalId
    }

  private fun dto(
    programme: RadioProgramme,
    airings: List<RadioAiring>,
    recordings: Int,
  ): RadioProgrammeDto =
    RadioProgrammeDto(
      id = programme.kukuId,
      name = programme.name,
      description = programme.description,
      recordEvery = programme.recordEvery,
      slots = slots(airings.filter { it.originalId == null }.map { it.startsAt to it.endsAt }, clock.zone),
      recordings = recordings,
    )

  companion object {
    private const val BYTES_PER_HOUR = (256_000L / 8 + RATE * 2L) * 3600
  }
}
