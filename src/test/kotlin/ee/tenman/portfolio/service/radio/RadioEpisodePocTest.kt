package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.readValue
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@EnabledIfEnvironmentVariable(named = "RADIO_WORKER_POC", matches = ".+")
class RadioEpisodePocTest {
  @TempDir
  lateinit var directory: Path

  private val log = LoggerFactory.getLogger(javaClass)

  private val reference = listOf(761.45, 2120.60, 2255.35, 3085.35, 3197.35, 3837.45)

  private fun prepare(poc: Path) {
    listOf("raw.mp3", "raw.pcm").forEach { Files.createSymbolicLink(directory.resolve(it), poc.resolve("replay").resolve(it)) }
    Files.copy(poc.resolve("replay/status.json"), directory.resolve("status.json"))
    Files.copy(poc.resolve("outlines.json"), directory.resolve("outline.json"))
    val windows = JsonMapperFactory.instance.readTree(Files.readString(poc.resolve("asr-windows.json")))["windows"].toList()
    val segments = windows.flatMap { it["segments"].toList() }
    val transcript =
      Transcript(
        segments.map { TranscriptSegment(it["start"].asDouble(), it["end"].asDouble(), it["text"].asString()) },
        segments.flatMap { it["words"].toList() }.map(::word),
      )
    Files.writeString(directory.resolve("transcript.json"), JsonMapperFactory.instance.writeValueAsString(transcript))
  }

  private fun word(node: JsonNode) = TranscriptWord(node["start"].asDouble(), node["end"].asDouble(), node["word"].asString())

  private fun errors(episode: Path): String {
    val command = listOf("ffmpeg", "-v", "error", "-i", episode.toString(), "-f", "null", "-")
    val process = ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    val output = process.errorStream.use { String(it.readBytes()) }
    check(process.waitFor(5, TimeUnit.MINUTES)) { "ffmpeg did not finish decoding $episode" }
    return output
  }

  @Test
  fun `should render the proof of concept episode from the saved reads`() {
    prepare(Path.of(System.getenv("RADIO_WORKER_POC")))
    val service = RadioEpisodeService(mockk(), RadioCutService(), RadioRenderService(), RadioProperties(), Clock.systemUTC())
    val started = System.nanoTime()
    service.make(directory)
    val episode = directory.resolve("episode.m4a")
    val peak = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.sumOf { it.peakUsage.used } / 1_048_576
    log.info("PoC episode took ${(System.nanoTime() - started) / 1_000_000_000} s with a peak heap of $peak MiB")
    val edit = JsonMapperFactory.instance.readValue<EditList>(Files.readString(directory.resolve("edit-list.json")))
    val found = probe(episode)
    val titles = found["chapters"].toList().map { it["tags"]["title"].asString() }
    val placed = edit.boundaries.map { it.seconds }
    expect(
      listOf(
        placed.zip(reference).all { (seconds, expected) -> abs(seconds - expected) <= 0.1 },
        abs(found["format"]["duration"].asString().toDouble() - 2829.2) <= 0.1,
        errors(episode),
        titles.size,
        titles[1],
        titles[3],
      ),
    ).toEqual(
      listOf(
        true,
        true,
        "",
        4,
        "Lõige 1 (22:39) – Üleminekud sotsidesse ja Eesti 200 kriis",
        "Lõige 2 (36:29) – President Ülle Madise ja tema nõunikud",
      ),
    )
  }
}
