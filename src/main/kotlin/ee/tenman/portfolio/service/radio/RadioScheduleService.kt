package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
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
    station: Station,
    airings: List<ScheduledAiring>,
    from: Instant,
    until: Instant,
  ) {
    check(airings.isNotEmpty()) { "${station.label} schedule from $from to $until lists no programmes" }
    store(station, airings, upsert(station, airings.map { it.show }.distinctBy { it.externalId }))
    radioAiringRepository.deleteDropped(station, from, until, airings.map { it.externalId })
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
    val programme = radioProgrammeRepository.findByExternalId(id) ?: return false
    programme.recordEvery = choice.recordEvery
    radioProgrammeRepository.save(programme)
    plan()
    return true
  }

  fun record(
    id: Long,
    choice: AiringChoice,
  ): Boolean {
    val airing = radioAiringRepository.findByExternalId(id) ?: return false
    require(airing.startsAt > clock.instant()) { "Kuku airing $id began at ${airing.startsAt} and can no longer be chosen" }
    airing.recordOnce = choice.record
    radioAiringRepository.save(airing)
    plan()
    return true
  }

  fun plan() {
    val root = radioProperties.recordings
    if (!Files.isDirectory(root)) return
    save(root.resolve(Station.KUKU.plan), planned(radioAiringRepository.findByEndsAtAfterOrderByStartsAt(clock.instant())))
  }

  fun station(): RadioStation {
    val now = clock.instant()
    val airings = radioAiringRepository.findByEndsAtAfterOrderByStartsAt(now)
    val captures = planned(airings)
    val (onAir, next) = airing(airings, captures, now)
    val root = radioProperties.recordings
    val free = root.takeIf(Files::isDirectory)?.let { Files.getFileStore(it).usableSpace }
    val recordings = radioArchiveService.recordings()
    val last = recordings.firstOrNull { it.status.state != RecordingState.RECORDING }
    return RadioStation(
      onAir = onAir,
      next = next,
      capture = captures.firstOrNull(),
      freeBytes = free,
      hours = free?.div(Station.KUKU.bytesPerHour),
      recorder = recorder(heartbeat(root), recordings.any { it.status.state == RecordingState.RECORDING }, now),
      last = last?.let { LastRecording(it.programme, (it.status.startedAt + it.status.seconds).toLong()) },
      format = last?.format,
    )
  }

  private fun airing(
    airings: List<RadioAiring>,
    captures: List<Capture>,
    now: Instant,
  ): Pair<StationAiring?, StationAiring?> {
    val onAir = airings.firstOrNull { it.startsAt <= now }
    val next = airings.firstOrNull { it.startsAt > now }
    val names = radioProgrammeRepository.findAllById(listOfNotNull(onAir, next).map { it.programmeId }).associate { it.id to it.name }
    return onAir?.let { live(it, names, captures) } to next?.let { live(it, names, captures) }
  }

  fun missed(): List<MissedCapture> {
    val root = radioProperties.recordings
    if (!Files.isDirectory(root)) return emptyList()
    val now = clock.instant()
    return missed(planned(radioAiringRepository.findByEndsAtAfterOrderByStartsAt(now - MISSED_FOR)), recorded(root), heartbeat(root), now)
  }

  private fun heartbeat(root: Path): Heartbeat? =
    root.resolve(Station.KUKU.heartbeat).takeIf(Files::exists)?.let {
      JsonMapperFactory.instance.readValue(Files.readString(it), Heartbeat::class.java)
    }

  fun planned(airings: List<RadioAiring>): List<Capture> {
    val programmes = radioProgrammeRepository.findAllById(airings.map { it.programmeId }.toSet()).associateBy { it.id }
    val planned =
      airings
        .filter { it.recordOnce || (it.originalId == null && programmes[it.programmeId]?.recordEvery == true) }
        .map { airing -> programmes.getValue(airing.programmeId).let { PlannedAiring(it.name, it.slug, airing.startsAt, airing.endsAt) } }
    return captures(planned, clock.zone)
  }

  private fun live(
    airing: RadioAiring,
    names: Map<Long, String>,
    captures: List<Capture>,
  ): StationAiring {
    val start = airing.startsAt.epochSecond
    val end = airing.endsAt.epochSecond
    return StationAiring(names.getValue(airing.programmeId), start, end, captures.cover(airing))
  }

  private fun upsert(
    station: Station,
    shows: List<ScheduledShow>,
  ): Map<Long, Long> {
    val existing = radioProgrammeRepository.findAllByExternalIdIn(shows.map { it.externalId }).associateBy { it.externalId }
    val saved =
      shows.map { show ->
        (existing[show.externalId] ?: RadioProgramme(show.externalId, show.name, show.slug, station = station)).apply {
          name = show.name
          slug = show.slug
          description = show.description
        }
      }
    return radioProgrammeRepository.saveAll(saved).associate { it.externalId to it.id }
  }

  private fun store(
    station: Station,
    airings: List<ScheduledAiring>,
    programmes: Map<Long, Long>,
  ) {
    val existing = radioAiringRepository.findAllByExternalIdIn(airings.map { it.externalId }).associateBy { it.externalId }
    radioAiringRepository.saveAll(
      airings.map { airing(station, existing[it.externalId], it, programmes.getValue(it.show.externalId)) },
    )
  }

  private fun airing(
    station: Station,
    existing: RadioAiring?,
    scheduled: ScheduledAiring,
    programmeId: Long,
  ): RadioAiring =
    (existing ?: RadioAiring(scheduled.externalId, programmeId, scheduled.title, scheduled.start, scheduled.end, station = station)).apply {
      this.programmeId = programmeId
      title = scheduled.title
      startsAt = scheduled.start
      endsAt = scheduled.end
      originalId = scheduled.originalExternalId
    }

  private fun dto(
    programme: RadioProgramme,
    airings: List<RadioAiring>,
    recordings: Int,
  ): RadioProgrammeDto =
    RadioProgrammeDto(
      id = programme.externalId,
      name = programme.name,
      description = programme.description,
      recordEvery = programme.recordEvery,
      slots = slots(airings.filter { it.originalId == null }.map { it.startsAt to it.endsAt }, clock.zone),
      recordings = recordings,
    )
}
