package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.mockk.every
import io.mockk.mockk
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

  @Test
  fun `should write a digest with a chapter per story`() {
    held()
    service().build(TODAY)
    val titles = probe(digest().resolve(EPISODE_FILE))["chapters"].toList().map { it["tags"]["title"].asString() }
    expect(titles).toEqual(listOf("Rünnakud Ust-Lugas", "Valimised"))
  }

  @Test
  fun `should keep the stories in rank order when two shows take turns`() {
    held(listOf(EpisodeChapter(0.0, "Ust-Luga"), EpisodeChapter(150.0, "Telia")))
    every { model.judge(any(), any()) } returns
      Judgement(listOf(story("Rünnakud Ust-Lugas", "r1", 5), story("Valimised", "r3", 4), story("Telia koondab", "r2", 3)))
    service().build(TODAY)
    expect(read<Digest>(digest().resolve(DIGEST_FILE))?.stories?.map { it.title })
      .toEqual(listOf("Rünnakud Ust-Lugas", "Valimised", "Telia koondab"))
  }

  @Test
  fun `should start each story's transcript at its place in the digest`() {
    held()
    service().build(TODAY)
    val second = read<List<TranscriptSection>>(digest().resolve(TRANSCRIPT_FILE)).orEmpty()[1]
    expect(second.lines.first().start to second.start).toEqual(181.0 to 181.0)
  }

  @Test
  fun `should leave the previous digest when the judge does not answer`() {
    held()
    writtenDigest(directory, TODAY.minusDays(1), emptyList())
    every { model.judge(any(), any()) } returns null
    runCatching { service().build(TODAY) }
    expect(Files.exists(digest(TODAY.minusDays(1)).resolve(DIGEST_FILE)) to Files.exists(digest())).toEqual(true to false)
  }

  @Test
  fun `should not build twice in one day`() {
    writtenDigest(directory, TODAY, emptyList())
    service().build(TODAY)
    verify(exactly = 0) { model.judge(any(), any()) }
  }

  @Test
  fun `should not be due once today's digest is built`() {
    writtenDigest(directory, TODAY, emptyList())
    expect(service().due(TODAY)).toEqual(false)
  }

  @Test
  fun `should mark a day without a story as built without audio`() {
    held()
    every { model.judge(any(), any()) } returns Judgement(emptyList())
    service().build(TODAY)
    val stories = read<Digest>(digest().resolve(DIGEST_FILE))?.stories
    expect(stories to Files.exists(digest().resolve(EPISODE_FILE))).toEqual(emptyList<DigestStory>() to false)
  }

  @Test
  fun `should keep the newest seven digests`() {
    held()
    (1L..8L).forEach { writtenDigest(directory, TODAY.minusDays(it), emptyList()) }
    every { model.judge(any(), any()) } returns Judgement(emptyList())
    service().build(TODAY)
    val kept = Files.list(directory.resolve(DIGEST_DIRECTORY)).use { days -> days.map { it.fileName.toString() }.toList() }
    expect(kept.sorted()).toEqual((6L downTo 0L).map { TODAY.minusDays(it).toString() })
  }

  @Test
  fun `should leave out a recording whose episode is not on disk`() {
    held()
    Files.delete(directory.resolve(SAMOST).resolve(EPISODE_FILE))
    every { model.judge(any(), any()) } returns Judgement(emptyList())
    service().build(TODAY)
    verify { model.judge(match { rows -> rows.map { it.recording } == listOf(KESKPAEVATUND) }, any()) }
  }

  @Test
  fun `should wait for a finished show before building`() {
    val finding = cutRecording(KESKPAEVATUND, listOf(EpisodeChapter(0.0, "Ust-Luga")), state = ProgrammeCutState.FINDING)
    every { archive.recordings() } returns listOf(finding)
    service().build(TODAY)
    expect(Files.exists(directory.resolve(DIGEST_DIRECTORY))).toEqual(false)
  }

  @Test
  fun `should judge only the shows of the days kept on disk`() {
    held()
    every { model.judge(any(), any()) } returns Judgement(emptyList())
    service(localDays = 2, now = "2026-11-16T12:00:00Z").build(TODAY)
    verify { model.judge(match { rows -> rows.map { it.recording } == listOf(SAMOST) }, any()) }
  }

  @Test
  fun `should leave a second of silence after each story`() {
    held()
    service().build(TODAY)
    val duration = probe(digest().resolve(EPISODE_FILE))["format"]["duration"].asString().toDouble()
    expect(abs(duration - 362.0) <= 0.1).toEqual(true)
  }

  @Test
  fun `should tag the digest audio with the station heard longest`() {
    held(listOf(EpisodeChapter(0.0, "Ust-Luga"), EpisodeChapter(150.0, "Telia")), Station.ARIPAEV)
    every { model.judge(any(), any()) } returns Judgement(listOf(story("Rünnakud Ust-Lugas", "r1", 5), story("Valimised", "r3", 4)))
    service().build(TODAY)
    expect(probe(digest().resolve(EPISODE_FILE))["format"]["tags"]["artist"].asString()).toEqual("Äripäev")
  }

  @Test
  fun `should clear the clips of a failed build before the next one`() {
    held()
    Files.writeString(directory.resolve(SAMOST).resolve(EPISODE_FILE), "Pole heli õ")
    runCatching { service().build(TODAY.minusDays(1)) }
    episode(SAMOST)
    service().build(TODAY)
    val kept = Files.list(directory.resolve(DIGEST_DIRECTORY)).use { folders -> folders.map { it.fileName.toString() }.toList() }
    expect(kept).toEqual(listOf(TODAY.toString()))
  }

  @Test
  fun `should stop trying after three failed builds in a day`() {
    val midnight = Instant.parse("2026-11-15T22:00:00Z")
    every { jobs.countByJobNameAndStatusAndStartTimeAfter("RadioDigestJob", JobStatus.FAILURE, midnight) } returns 3
    expect(service().due(TODAY)).toEqual(false)
  }

  private fun service(
    localDays: Long = 7,
    now: String = "2026-11-16T10:00:00Z",
  ) = RadioDigestService(
    archive,
    model,
    RadioRenderService(),
    RadioProperties(recordings = directory, localDays = localDays),
    jobs,
    Clock.fixed(Instant.parse(now), ZoneId.of("Europe/Tallinn")),
  )

  private fun held(
    chapters: List<EpisodeChapter> = listOf(EpisodeChapter(0.0, "Ust-Luga")),
    station: Station = Station.KUKU,
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
    listOf(first, second).forEach { episode(it.name) }
    every { archive.recordings() } returns listOf(second, first)
    every { archive.transcript(KESKPAEVATUND) } returns listOf(TranscriptLine(0.0, 5.0, "Sõda on lähedal.", emptyList()))
    every { archive.transcript(SAMOST) } returns listOf(TranscriptLine(0.0, 5.0, "Valimised tulevad.", emptyList()))
    every { model.judge(any(), any()) } returns Judgement(listOf(story("Rünnakud Ust-Lugas", "r1", 5), story("Valimised", "r2", 4)))
    every { model.clip(any(), any()) } returns null
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

  private fun digest(date: LocalDate = TODAY): Path = directory.resolve(DIGEST_DIRECTORY).resolve(date.toString())

  companion object {
    private val TODAY = LocalDate.of(2026, 11, 16)
    private const val KESKPAEVATUND = "2026-11-14-1200-keskpaevatund"
    private const val SAMOST = "2026-11-15-1200-samost-ja-aaspollu"
    private const val SUNDAY_NOON = 1_794_736_800.0
  }
}
