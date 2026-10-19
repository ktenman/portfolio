package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class RadioDayService(
  private val radioAiringRepository: RadioAiringRepository,
  private val radioProgrammeRepository: RadioProgrammeRepository,
  private val radioArchiveService: RadioArchiveService,
  private val radioScheduleService: RadioScheduleService,
  private val clock: Clock,
) {
  fun day(date: LocalDate?): RadioDay {
    val today = LocalDate.now(clock)
    val shown = date ?: today
    val now = clock.instant().epochSecond
    val takes = radioArchiveService.recordings().map { it.take(now) }
    val from = shown.atStartOfDay(clock.zone).toEpochSecond()
    val until = shown.plusDays(1).atStartOfDay(clock.zone).toEpochSecond()
    val airings = radioAiringRepository.findStarting(start(shown), start(shown.plusDays(1)))
    val (day, night) = rows(airings, takes, now).partition { it.start < until }
    val recorded = spans(takes, from, until)
    return RadioDay(
      date = shown,
      today = today,
      days = listOf(today.minusDays(1), today, today.plusDays(1)),
      from = from,
      until = until,
      now = now,
      recorded = recorded,
      seconds = recorded.sumOf { it.end - it.start },
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
      radioAiringRepository.findAllByKukuIdIn(airings.mapNotNull { it.originalId }).associate { it.kukuId to original(it, takes, now) }
    val captures = radioScheduleService.planned(airings)
    return airings.map { row(it, names.getValue(it.programmeId), it.originalId?.let(originals::get), captures.cover(it), takes, now) }
  }

  private fun List<Capture>.cover(airing: RadioAiring): Boolean =
    any { it.start <= airing.startsAt.epochSecond && it.end >= airing.endsAt.epochSecond }

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
      id = airing.kukuId,
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
