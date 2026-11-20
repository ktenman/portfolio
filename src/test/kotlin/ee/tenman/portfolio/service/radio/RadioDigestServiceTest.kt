package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.repository.JobExecutionRepository
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class RadioDigestServiceTest {
  @TempDir
  lateinit var directory: Path

  private val archive = mockk<RadioArchiveService>()
  private val model = mockk<RadioDigestModelService>()
  private val jobs = mockk<JobExecutionRepository> { every { countByJobNameAndStatusAndStartTimeAfter(any(), any(), any()) } returns 0 }
  private val progress = spyk(RadioDigestProgressService(LiveUpdateService(), Clock.fixed(Instant.parse(NOW), ZoneId.of("Europe/Tallinn"))))

  @Test
  fun `should report choosing the stories while the judge reads`() {
    held(audio = false)
    var step: DigestStep? = null
    every { model.judge(any(), any(), any()) } answers {
      step = progress.current()?.step
      Judgement(emptyList())
    }
    service().build(DigestShow.WEEK, TODAY)
    expect(step).toEqual(DigestStep.STORIES)
  }

  @Test
  fun `should list the stories picked while their clips are chosen`() {
    held(audio = false)
    var titles: List<String>? = null
    every { model.clip(any(), any()) } answers {
      titles = progress.current()?.titles
      null
    }
    service(renderer = silent()).build(DigestShow.WEEK, TODAY)
    expect(titles).toEqual(listOf("Rünnakud Ust-Lugas", "Valimised"))
  }

  @Test
  fun `should count each clip as it is cut`() {
    held(audio = false)
    service(renderer = silent()).build(DigestShow.WEEK, TODAY)
    verify(exactly = 2) { progress.cut() }
  }

  @Test
  fun `should follow the mix`() {
    held(audio = false)
    service(renderer = silent()).build(DigestShow.WEEK, TODAY)
    verify(atLeast = 1) { progress.mixed(any()) }
  }

  @Test
  fun `should forget the build once it is written`() {
    held(audio = false)
    service(renderer = silent()).build(DigestShow.WEEK, TODAY)
    expect(progress.current()).toEqual(null)
  }

  @Test
  fun `should forget the build when it fails`() {
    held(audio = false)
    every { model.judge(any(), any(), any()) } returns null
    runCatching { service().build(DigestShow.WEEK, TODAY) }
    expect(progress.current()).toEqual(null)
  }

  @Test
  fun `should show the running build`() {
    progress.started(DigestShow.WEEK, TODAY)
    expect(service().building()?.step).toEqual(DigestStep.STORIES)
  }

  @Test
  fun `should name the week in the running build`() {
    progress.started(DigestShow.WEEK, TODAY)
    expect(service().building()?.title).toEqual("Week in Radio 09.11–15.11.2026")
  }

  @Test
  fun `should show today's failure with the next hourly try`() {
    failed(1)
    expect(service(now = "2026-11-16T04:30:00Z").building()?.failure).toEqual(DigestFailure(REASON, 1, 3, "2026-11-16T05:00:00Z"))
  }

  @Test
  fun `should say it stopped for the day after the last hourly try`() {
    failed(1)
    expect(service(now = "2026-11-16T07:30:00Z").building()?.failure?.nextTry).toEqual(null)
  }

  @Test
  fun `should give only the first line of a long failure`() {
    failed(1, "FFmpeg exited with 1: \n[mp3 @ 0x1] Header missing õ\nError while decoding stream")
    expect(service().building()?.failure?.reason).toEqual("FFmpeg exited with 1:")
  }

  @Test
  fun `should name a failure without a message`() {
    failed(1, " ")
    expect(service().building()?.failure?.reason).toEqual("The digest build failed")
  }

  @Test
  fun `should say it stopped for the day after three failures`() {
    failed(3)
    expect(service(now = "2026-11-16T04:30:00Z").building()?.failure).toEqual(DigestFailure(REASON, 3, 3, null))
  }

  @Test
  fun `should show no failure once today's digest is built`() {
    failed(1)
    writtenDigest(directory, TODAY, listOf("Valimised"))
    expect(service().building()).toEqual(null)
  }

  @Test
  fun `should show nothing on a day without a failure`() {
    expect(service().building()).toEqual(null)
  }

  @Test
  fun `should write a digest with a chapter per story`() {
    held()
    service().build(DigestShow.WEEK, TODAY)
    val titles = probe(digest().resolve(EPISODE_FILE))["chapters"].toList().map { it["tags"]["title"].asString() }
    expect(titles).toEqual(listOf("Rünnakud Ust-Lugas", "Valimised"))
  }

  @Test
  fun `should title the digest audio with the week it covers`() {
    held()
    service().build(DigestShow.WEEK, TODAY)
    expect(probe(digest().resolve(EPISODE_FILE))["format"]["tags"]["title"].asString()).toEqual("Week in Radio 09.11–15.11.2026")
  }

  @Test
  fun `should keep the stories in rank order when two shows take turns`() {
    held(listOf(EpisodeChapter(0.0, "Ust-Luga"), EpisodeChapter(150.0, "Telia")), audio = false)
    every { model.judge(any(), any(), any()) } returns
      Judgement(listOf(story("Rünnakud Ust-Lugas", "r1", 5), story("Valimised", "r3", 4), story("Telia koondab", "r2", 3)))
    service(renderer = silent()).build(DigestShow.WEEK, TODAY)
    expect(read<Digest>(digest().resolve(DIGEST_FILE))?.stories?.map { it.title })
      .toEqual(listOf("Rünnakud Ust-Lugas", "Valimised", "Telia koondab"))
  }

  @Test
  fun `should start each story's transcript at its place in the digest`() {
    held(audio = false)
    service(renderer = silent()).build(DigestShow.WEEK, TODAY)
    val second = read<List<TranscriptSection>>(digest().resolve(TRANSCRIPT_FILE)).orEmpty()[1]
    expect(second.lines.first().start to second.start).toEqual(181.0 to 181.0)
  }

  @Test
  fun `should leave the previous digest when the judge does not answer`() {
    held(audio = false)
    writtenDigest(directory, TODAY.minusDays(1), emptyList())
    every { model.judge(any(), any(), any()) } returns null
    runCatching { service().build(DigestShow.WEEK, TODAY) }
    expect(Files.exists(digest(TODAY.minusDays(1)).resolve(DIGEST_FILE)) to Files.exists(digest())).toEqual(true to false)
  }

  @Test
  fun `should not build twice in one day`() {
    writtenDigest(directory, TODAY, emptyList())
    service().build(DigestShow.WEEK, TODAY)
    verify(exactly = 0) { model.judge(any(), any(), any()) }
  }

  @Test
  fun `should not be due once today's digest is built`() {
    writtenDigest(directory, TODAY, emptyList())
    expect(service().due(DigestShow.WEEK, TODAY)).toEqual(false)
  }

  @Test
  fun `should mark a day without a story as built without audio`() {
    held(audio = false)
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service().build(DigestShow.WEEK, TODAY)
    val stories = read<Digest>(digest().resolve(DIGEST_FILE))?.stories
    expect(stories to Files.exists(digest().resolve(EPISODE_FILE))).toEqual(emptyList<DigestStory>() to false)
  }

  @Test
  fun `should keep the newest seven digests`() {
    held(audio = false)
    (1L..8L).forEach { writtenDigest(directory, TODAY.minusDays(it), emptyList()) }
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service().build(DigestShow.WEEK, TODAY)
    val kept = Files.list(directory.resolve(DigestShow.WEEK.directory)).use { days -> days.map { it.fileName.toString() }.toList() }
    expect(kept.sorted()).toEqual((6L downTo 0L).map { TODAY.minusDays(it).toString() })
  }

  @Test
  fun `should leave out a recording whose episode is not on disk`() {
    held(audio = false)
    Files.delete(directory.resolve(SAMOST).resolve(EPISODE_FILE))
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service().build(DigestShow.WEEK, TODAY)
    verify { model.judge(any(), match { rows -> rows.map { it.recording } == listOf(KESKPAEVATUND) }, any()) }
  }

  @Test
  fun `should wait for a finished show before building`() {
    val finding = cutRecording(KESKPAEVATUND, listOf(EpisodeChapter(0.0, "Ust-Luga")), state = ProgrammeCutState.FINDING)
    every { archive.recordings() } returns listOf(finding)
    service().build(DigestShow.WEEK, TODAY)
    expect(Files.exists(directory.resolve(DigestShow.WEEK.directory))).toEqual(false)
  }

  @Test
  fun `should judge only the shows of the days kept on disk`() {
    held(audio = false)
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service(localDays = 2, now = "2026-11-16T12:00:00Z").build(DigestShow.WEEK, TODAY)
    verify { model.judge(any(), match { rows -> rows.map { it.recording } == listOf(SAMOST) }, any()) }
  }

  @Test
  fun `should tell the judge only the stories of earlier weeks`() {
    held()
    writtenDigest(directory, TODAY.minusWeeks(1), listOf("Valimised"))
    writtenDigest(directory, TODAY.minusDays(3), listOf("Telia koondab"))
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service().build(DigestShow.WEEK, TODAY)
    verify { model.judge(any(), any(), listOf(TODAY.minusWeeks(1) to "Valimised")) }
  }

  @Test
  fun `should write the day show into its own folder`() {
    held()
    service().build(DigestShow.DAY, TODAY)
    val written = Files.exists(directory.resolve(DigestShow.DAY.directory).resolve(TODAY.toString()).resolve(DIGEST_FILE))
    expect(written to Files.exists(directory.resolve(DigestShow.WEEK.directory))).toEqual(true to false)
  }

  @Test
  fun `should count the failures of each show apart`() {
    every { jobs.countByJobNameAndStatusAndStartTimeAfter("RadioDayDigestJob", JobStatus.FAILURE, any()) } returns 3
    expect(service().due(DigestShow.DAY, TODAY) to service().due(DigestShow.WEEK, TODAY)).toEqual(false to true)
  }

  @Test
  fun `should tell the day judge only earlier day shows`() {
    held()
    writtenDigest(directory, TODAY.minusDays(1), listOf("Valimised"), DigestShow.DAY)
    writtenDigest(directory, TODAY.minusWeeks(1), listOf("Telia koondab"))
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service().build(DigestShow.DAY, TODAY)
    verify { model.judge(any(), any(), listOf(TODAY.minusDays(1) to "Valimised")) }
  }

  @Test
  fun `should show the day show's failure with its next try at half past`() {
    failed(1, show = DigestShow.DAY)
    val build = service(now = "2026-11-16T10:40:00Z").building()
    expect(build?.title to build?.failure?.nextTry).toEqual("Day in Radio 16.11.2026" to "2026-11-16T11:30:00Z")
  }

  @Test
  fun `should show a failed rebuild while no digest builds`() {
    progress.failed(DigestShow.DAY, TODAY, "Mudel ei vastanud õigel ajal")
    expect(service().building()?.failure?.reason).toEqual("Mudel ei vastanud õigel ajal")
  }

  @Test
  fun `should show the running build before the day show's failure`() {
    failed(1, show = DigestShow.DAY)
    progress.started(DigestShow.WEEK, TODAY)
    expect(service().building()?.step).toEqual(DigestStep.STORIES)
  }

  @Test
  fun `should show the day show's failure before the week's`() {
    failed(1)
    failed(1, show = DigestShow.DAY)
    expect(service().building()?.title).toEqual("Day in Radio 16.11.2026")
  }

  @Test
  fun `should mark the shows of the day before the news as earlier`() {
    ended(10, 30, 50)
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    service().build(DigestShow.DAY, TODAY)
    val marked = listOf("eetris-30" to true, "eetris-10" to false)
    verify { model.judge(DigestShow.DAY, match { rows -> rows.map { it.recording to it.earlier } == marked }, any()) }
  }

  @Test
  fun `should not build the day show without news`() {
    ended(30)
    service().build(DigestShow.DAY, TODAY)
    expect(Files.exists(directory.resolve(DigestShow.DAY.directory))).toEqual(false)
  }

  @Test
  fun `should leave a second of silence after each story`() {
    held()
    service().build(DigestShow.WEEK, TODAY)
    val duration = probe(digest().resolve(EPISODE_FILE))["format"]["duration"].asString().toDouble()
    expect(abs(duration - 362.0) <= 0.1).toEqual(true)
  }

  @Test
  fun `should start a story after the jingle of its station`() {
    held()
    jingle()
    service().build(DigestShow.WEEK, TODAY)
    expect(read<Digest>(digest().resolve(DIGEST_FILE))?.stories?.map { it.start }).toEqual(listOf(0.0, 185.4))
  }

  @Test
  fun `should play the jingle of the station between two stories`() {
    held()
    jingle()
    service().build(DigestShow.WEEK, TODAY)
    val duration = probe(digest().resolve(EPISODE_FILE))["format"]["duration"].asString().toDouble()
    expect(abs(duration - 366.4) <= 0.1).toEqual(true)
  }

  @Test
  fun `should keep the saved jingle after it built the digest`() {
    held()
    val saved = jingle()
    service().build(DigestShow.WEEK, TODAY)
    expect(Files.exists(saved)).toEqual(true)
  }

  @Test
  fun `should tag the digest audio with the station heard longest`() {
    held(listOf(EpisodeChapter(0.0, "Ust-Luga"), EpisodeChapter(150.0, "Telia")), Station.ARIPAEV)
    every { model.judge(any(), any(), any()) } returns Judgement(listOf(story("Rünnakud Ust-Lugas", "r1", 5), story("Valimised", "r3", 4)))
    service().build(DigestShow.WEEK, TODAY)
    expect(probe(digest().resolve(EPISODE_FILE))["format"]["tags"]["artist"].asString()).toEqual("Äripäev")
  }

  @Test
  fun `should clear the clips of a failed build before the next one`() {
    held()
    Files.writeString(directory.resolve(SAMOST).resolve(EPISODE_FILE), "Pole heli õ")
    runCatching { service().build(DigestShow.WEEK, TODAY.minusDays(1)) }
    episode(SAMOST)
    service().build(DigestShow.WEEK, TODAY)
    val kept = Files.list(directory.resolve(DigestShow.WEEK.directory)).use { folders -> folders.map { it.fileName.toString() }.toList() }
    expect(kept).toEqual(listOf(TODAY.toString()))
  }

  @Test
  fun `should stop trying after three failed builds in a day`() {
    val midnight = Instant.parse("2026-11-15T22:00:00Z")
    every { jobs.countByJobNameAndStatusAndStartTimeAfter("RadioDigestJob", JobStatus.FAILURE, midnight) } returns 3
    expect(service().due(DigestShow.WEEK, TODAY)).toEqual(false)
  }

  @Test
  fun `should replace today's digest when it is rebuilt`() {
    held(audio = false)
    writtenDigest(directory, TODAY, listOf("Vana lugu"))
    service(renderer = silent()).rebuild(DigestShow.WEEK, TODAY)
    expect(Files.readString(digest().resolve(DIGEST_FILE))).notToContain("Vana lugu")
  }

  @Test
  fun `should rebuild after today's tries are used up`() {
    held(audio = false)
    failed(3)
    service(renderer = silent()).rebuild(DigestShow.WEEK, TODAY)
    expect(Files.exists(digest().resolve(DIGEST_FILE))).toEqual(true)
  }

  @Test
  fun `should keep today's digest when the rebuild fails`() {
    held(audio = false)
    writtenDigest(directory, TODAY, listOf("Vana lugu"))
    val renderer = mockk<RadioRenderService> { every { silence(any(), any()) } throws IllegalStateException("FFmpeg lõpetas veaga") }
    runCatching { service(renderer = renderer).rebuild(DigestShow.WEEK, TODAY) }
    expect(Files.readString(digest().resolve(DIGEST_FILE))).toContain("Vana lugu")
  }

  @Test
  fun `should say that no recordings are ready to rebuild from`() {
    every { archive.recordings() } returns emptyList()
    expect { service().rebuild(DigestShow.WEEK, TODAY) }
      .toThrow<IllegalStateException>()
      .messageToContain("No recordings are ready for the 2026-11-16 radio digest")
  }

  @Test
  fun `should keep the earlier week's digest beside a midweek rebuild`() {
    held(audio = false)
    writtenDigest(directory, TODAY.minusDays(2), listOf("Eelmine nädal"))
    service(renderer = silent()).rebuild(DigestShow.WEEK, TODAY)
    expect(Files.exists(digest(TODAY.minusDays(2)).resolve(DIGEST_FILE))).toEqual(true)
  }

  @Test
  fun `should leave the running build alone when a scheduled one is refused`() {
    held(audio = false)
    progress.started(DigestShow.DAY, TODAY)
    service().build(DigestShow.WEEK, TODAY)
    expect(progress.current()?.show).toEqual(DigestShow.DAY)
  }

  @Test
  fun `should not be due while another digest builds`() {
    progress.started(DigestShow.DAY, TODAY)
    expect(service().due(DigestShow.WEEK, TODAY)).toEqual(false)
  }

  @Test
  fun `should be due while only a failed rebuild is held`() {
    progress.started(DigestShow.DAY, TODAY)
    progress.failed(DigestShow.DAY, TODAY, "Mudel ei vastanud õigel ajal")
    expect(service().due(DigestShow.WEEK, TODAY)).toEqual(true)
  }

  @Test
  fun `should keep today's digest when the rebuild finds no story`() {
    held(audio = false)
    writtenDigest(directory, TODAY, listOf("Vana lugu"))
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    runCatching { service().rebuild(DigestShow.WEEK, TODAY) }
    expect(Files.readString(digest().resolve(DIGEST_FILE))).toContain("Vana lugu")
  }

  @Test
  fun `should say that no story made the rebuilt digest`() {
    held(audio = false)
    writtenDigest(directory, TODAY, listOf("Vana lugu"))
    every { model.judge(any(), any(), any()) } returns Judgement(emptyList())
    expect { service().rebuild(DigestShow.WEEK, TODAY) }
      .toThrow<IllegalStateException>()
      .messageToContain("No story made the 2026-11-16 radio digest")
  }

  private fun service(
    localDays: Long = 7,
    now: String = NOW,
    renderer: RadioRenderService = RadioRenderService(),
  ) = RadioDigestService(
    archive,
    model,
    renderer,
    progress,
    RadioProperties(recordings = directory, localDays = localDays),
    jobs,
    Clock.fixed(Instant.parse(now), ZoneId.of("Europe/Tallinn")),
  )

  private fun silent() =
    mockk<RadioRenderService> {
      every { silence(any(), any()) } answers { Files.createFile(firstArg<Path>()) }
      every { clip(any(), any(), any(), any()) } answers { Files.createFile(arg<Path>(3)) }
      every { render(any(), any(), any(), any(), any(), any(), any()) } answers {
        lastArg<(Double) -> Unit>()(1.0)
        Files.createFile(arg<Path>(4))
      }
    }

  private fun held(
    chapters: List<EpisodeChapter> = listOf(EpisodeChapter(0.0, "Ust-Luga")),
    station: Station = Station.KUKU,
    audio: Boolean = true,
  ) {
    val audible = listOf(KeptSpan(0.0, 300.0))
    val first = cutRecording(KESKPAEVATUND, chapters, audible)
    val second =
      cutRecording(
        SAMOST,
        listOf(EpisodeChapter(0.0, "Valimised")),
        audible,
        programme = "Samost ja Aaspõllu",
        startedAt = SUNDAY_NOON,
        station = station,
      )
    listOf(first, second).forEach { if (audio) episode(it.name) else stub(it.name) }
    every { archive.recordings() } returns listOf(second, first)
    every { archive.transcript(KESKPAEVATUND) } returns listOf(TranscriptLine(0.0, 5.0, "Sõda on lähedal.", emptyList()))
    every { archive.transcript(SAMOST) } returns listOf(TranscriptLine(0.0, 5.0, "Valimised tulevad.", emptyList()))
    every { model.judge(any(), any(), any()) } returns Judgement(listOf(story("Rünnakud Ust-Lugas", "r1", 5), story("Valimised", "r2", 4)))
    every { model.clip(any(), any()) } returns null
  }

  private fun ended(vararg hours: Long) {
    val recordings =
      hours.map { ago ->
        val start = Instant.parse(NOW).epochSecond - (ago + 1) * 3600.0
        cutRecording("eetris-$ago", listOf(EpisodeChapter(0.0, "Uudised õhtul")), startedAt = start)
      }
    recordings.forEach { episode(it.name) }
    every { archive.recordings() } returns recordings
  }

  private fun failed(
    tries: Long,
    message: String? = REASON,
    show: DigestShow = DigestShow.WEEK,
  ) {
    every { jobs.countByJobNameAndStatusAndStartTimeAfter(show.job, any(), any()) } returns tries
    val at = Instant.parse("2026-11-16T09:00:00Z")
    every { jobs.findFirstByJobNameAndStatusOrderByStartTimeDesc(show.job, JobStatus.FAILURE) } returns
      JobExecution(show.job, at, at.plusSeconds(69), 69_000, JobStatus.FAILURE, message)
  }

  private fun story(
    title: String,
    row: String,
    importance: Int,
  ) = JudgedStory(title, "Kokkuvõte", listOf(row), row, "news", importance, 5, false, "Põhjus")

  private fun episode(name: String) {
    val path = Files.createDirectories(directory.resolve(name)).resolve(EPISODE_FILE)
    ffmpeg("-f", "lavfi", "-i", "aevalsrc='0.3*sin(2*PI*440*t)':s=48000:d=300", "-c:a", "aac", "-b:a", "96k", "$path")
  }

  private fun jingle(): Path {
    val library = Files.createDirectories(directory.resolve("idents"))
    val ident = Ident(Station.KUKU, "Keskpäevatund", "kuku-keskpaevatund.m4a", 5.0, "2026-11-14T10:00:00Z")
    val path = library.resolve(ident.file)
    ffmpeg("-f", "lavfi", "-i", "aevalsrc='0.2*sin(2*PI*880*t)':s=48000:d=5", "-c:a", "aac", "-b:a", "192k", "$path")
    save(library.resolve(IDENTS_FILE), listOf(ident))
    return path
  }

  private fun stub(name: String) {
    Files.writeString(Files.createDirectories(directory.resolve(name)).resolve(EPISODE_FILE), "Pole heli õ")
  }

  private fun digest(date: LocalDate = TODAY): Path = directory.resolve(DigestShow.WEEK.directory).resolve(date.toString())

  companion object {
    private val TODAY = LocalDate.of(2026, 11, 16)
    private const val NOW = "2026-11-16T10:00:00Z"
    private const val REASON = "The model gave no judgement for the 2026-11-16 radio digest"
    private const val KESKPAEVATUND = "2026-11-14-1200-keskpaevatund"
    private const val SAMOST = "2026-11-15-1200-samost-ja-aaspollu"
    private const val SUNDAY_NOON = 1_794_736_800.0
  }
}
