package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class RadioDayService(
  private val radioAiringRepository: RadioAiringRepository,
  private val radioProgrammeRepository: RadioProgrammeRepository,
  private val radioArchiveService: RadioArchiveService,
  private val radioScheduleService: RadioScheduleService,
  private val clock: Clock,
) {
  fun day(date: LocalDate?): RadioDay {
    val today = LocalDateTime.now(clock).minusSeconds(DAY_STARTS.toSecondOfDay().toLong()).toLocalDate()
    val shown = date ?: today
    val now = clock.instant().epochSecond
    val takes = radioArchiveService.recordings().map { it.take(now) }
    val from = start(shown)
    val until = start(shown.plusDays(1))
    val midnight = shown.plusDays(1).atStartOfDay(clock.zone).toEpochSecond()
    val airings = radioAiringRepository.findStarting(from, until)
    val rows = rows(airings, takes, now)
    val (day, night) = rows.partition { it.start < midnight }
    val recorded = spans(takes, from.epochSecond, until.epochSecond)
    val plan = planned(rows, until.epochSecond)
    return RadioDay(
      date = shown,
      today = today,
      days = week(today),
      from = from.epochSecond,
      until = until.epochSecond,
      ticks = ticks(shown, clock.zone),
      now = now,
      recorded = recorded,
      seconds = recorded.sumOf { it.end - it.start },
      planned = plan,
      plannedSeconds = plan.sumOf { it.end - it.start },
      day = blocks(day),
      night = blocks(night),
    )
  }

  private fun start(date: LocalDate): Instant = date.atTime(DAY_STARTS).atZone(clock.zone).toInstant()

  private fun rows(
    airings: List<RadioAiring>,
    takes: List<Take>,
    now: Long,
  ): List<DayAiring> {
    val names = radioProgrammeRepository.findAllById(airings.map { it.programmeId }.toSet()).associate { it.id to it.name }
    val originals =
      radioAiringRepository
        .findAllByExternalIdIn(airings.mapNotNull { it.originalId })
        .associate { it.externalId to original(it, takes, now) }
    val captures = radioScheduleService.planned(airings)
    return airings.map { row(it, names.getValue(it.programmeId), it.originalId?.let(originals::get), captures.cover(it), takes, now) }
  }

  private fun row(
    airing: RadioAiring,
    name: String,
    original: OriginalAiring?,
    planned: Boolean,
    takes: List<Take>,
    now: Long,
  ): DayAiring {
    val start = airing.startsAt.epochSecond
    val end = airing.endsAt.epochSecond
    val recording = covered(start, end, takes)
    return DayAiring(
      id = airing.externalId,
      name = name,
      episode = episode(airing.title, name),
      start = start,
      end = end,
      state = state(start, end, now, recording, planned, original?.state),
      once = airing.recordOnce,
      repeat = airing.originalId != null,
      original = original,
      recording = recording,
    )
  }

  private fun original(
    airing: RadioAiring,
    takes: List<Take>,
    now: Long,
  ): OriginalAiring {
    val start = airing.startsAt.epochSecond
    val end = airing.endsAt.epochSecond
    val recording = covered(start, end, takes)
    return OriginalAiring(start, state(start, end, now, recording, false, null), recording)
  }
}
