package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.service.radio.EditList
import ee.tenman.portfolio.service.radio.KeptSpan
import ee.tenman.portfolio.service.radio.OutlineSummary
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioStorageService
import ee.tenman.portfolio.service.radio.RecordingPart
import ee.tenman.portfolio.service.radio.RecordingState
import ee.tenman.portfolio.service.radio.RecordingStatus
import ee.tenman.portfolio.service.radio.Transcript
import ee.tenman.portfolio.service.radio.TranscriptSegment
import ee.tenman.portfolio.service.radio.ffmpeg
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_RANGE
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpHeaders.RANGE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete as mvcDelete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as mvcPost
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put as mvcPut

@IntegrationTest
class RadioControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var radioProperties: RadioProperties

  @Resource
  private lateinit var radioStorageService: RadioStorageService

  private lateinit var directory: Path

  @BeforeEach
  fun setup() {
    radioProperties.recordings.toFile().deleteRecursively()
    directory = Files.createDirectories(radioProperties.recordings.resolve("2026-10-17"))
    val status = RecordingStatus(RecordingState.CAPTURED, 1792230900.0, 4200.0, listOf(RecordingPart("raw.mp3", 0.0)))
    Files.writeString(directory.resolve("status.json"), JsonMapperFactory.instance.writeValueAsString(status))
    Files.writeString(directory.resolve("episode.m4a"), "Keskpäevatund õ")
  }

  @Test
  fun `should pass the live station on as MP3`() {
    val playlist = Files.createDirectories(Path.of(radioProperties.live).parent).resolve("stream.m3u8")
    val tone = arrayOf("-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:a", "libmp3lame")
    ffmpeg(*tone, "-f", "hls", "-hls_list_size", "0", playlist.toString())
    mockMvc.perform(mvcGet("/api/radio/live")).andExpect(status().isOk).andExpect(header().string(CONTENT_TYPE, "audio/mpeg"))
  }

  @Test
  fun `should name the last recording and when it ended`() {
    mockMvc.perform(mvcGet("/api/radio/station")).andExpect(status().isOk).andExpect(jsonPath("$.last.end").value(1792235100))
  }

  @Test
  fun `should list the recordings`() {
    mockMvc.perform(mvcGet("/api/radio")).andExpect(status().isOk).andExpect(jsonPath("$[0].name").value("2026-10-17"))
  }

  @Test
  fun `should serve the recording state as the worker writes it`() {
    mockMvc.perform(mvcGet("/api/radio")).andExpect(jsonPath("$[0].status.state").value("captured"))
  }

  @Test
  fun `should serve a byte range of the episode`() {
    mockMvc
      .perform(mvcGet("/api/radio/2026-10-17/episode.m4a").header(RANGE, "bytes=0-3"))
      .andExpect(status().isPartialContent)
      .andExpect(header().string(CONTENT_RANGE, "bytes 0-3/17"))
  }

  @Test
  fun `should serve the text transcript as UTF-8`() {
    val transcript = Transcript(listOf(TranscriptSegment(1.0, 2.5, "Tere, Keskpäevatund!")), emptyList())
    Files.writeString(directory.resolve("transcript.json"), JsonMapperFactory.instance.writeValueAsString(transcript))
    mockMvc
      .perform(mvcGet("/api/radio/2026-10-17/transcript.txt"))
      .andExpect(header().string(CONTENT_TYPE, "text/plain;charset=UTF-8"))
  }

  @Test
  fun `should serve a byte range of an episode kept only in the bucket`() {
    radioStorageService.store()
    Files.deleteIfExists(directory.resolve("episode.m4a"))
    mockMvc
      .perform(mvcGet("/api/radio/2026-10-17/episode.m4a").header(RANGE, "bytes=4-7"))
      .andExpect(status().isPartialContent)
      .andExpect(content().bytes("päe".toByteArray()))
  }

  @Test
  fun `should not serve the status file`() {
    mockMvc.perform(mvcGet("/api/radio/2026-10-17/status.json")).andExpect(status().isNotFound)
  }

  @Test
  fun `should not find an unknown recording`() {
    mockMvc.perform(mvcGet("/api/radio/2026-10-31/episode.m4a")).andExpect(status().isNotFound)
  }

  @Test
  fun `should not find the transcript of a recording without one`() {
    mockMvc.perform(mvcGet("/api/radio/2026-10-17/transcript")).andExpect(status().isNotFound)
  }

  @Test
  fun `should reject a cut the recording does not have`() {
    val outline = OutlineSummary(null, 1, 1, 1)
    val edit = EditList(listOf(KeptSpan(761.5, 3837.38)), 3075.88, emptyList(), emptyList(), emptyList(), outline, emptyList(), false, "")
    Files.writeString(directory.resolve("edit-list.json"), JsonMapperFactory.instance.writeValueAsString(edit))
    mockMvc
      .perform(
        mvcPut(
          "/api/radio/2026-10-17/cuts",
        ).contentType(APPLICATION_JSON).content("""{"removed":[5]}"""),
      ).andExpect(status().isBadRequest)
  }

  @Test
  fun `should not add a cut by hand to a recording without an edit list`() {
    mockMvc
      .perform(mvcPost("/api/radio/2026-10-17/cuts/manual").contentType(APPLICATION_JSON).content("""{"start":20.0,"end":40.0}"""))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `should ask for the cuts of a programme the recording holds`() {
    mockMvc
      .perform(mvcPost("/api/radio/2026-10-17/cuts/find").contentType(APPLICATION_JSON).content("""{"programme":"Keskpäevatund"}"""))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `should reject the cuts of a programme the recording does not hold`() {
    mockMvc
      .perform(mvcPost("/api/radio/2026-10-17/cuts/find").contentType(APPLICATION_JSON).content("""{"programme":"Tarkade klubi"}"""))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should not find the cuts of a recording without a transcript`() {
    mockMvc.perform(mvcGet("/api/radio/2026-10-17/cuts")).andExpect(status().isNotFound)
  }

  @Test
  fun `should not find the jingle of a recording without an edit list`() {
    mockMvc
      .perform(mvcPut("/api/radio/2026-10-17/cuts/1/jingle").contentType(APPLICATION_JSON).content("""{"saved":true}"""))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `should not mark a missing transcript checked`() {
    mockMvc.perform(mvcPut("/api/radio/2026-10-17/transcript/checked")).andExpect(status().isNotFound)
  }

  @Test
  fun `should delete a recording`() {
    mockMvc.perform(mvcDelete("/api/radio/2026-10-17")).andExpect(status().isNoContent)
  }

  @Test
  fun `should refuse to delete a recording on air`() {
    val onAir = RecordingStatus(RecordingState.RECORDING, 1792230900.0, 60.0, listOf(RecordingPart("raw.mp3", 0.0)))
    Files.writeString(directory.resolve("status.json"), JsonMapperFactory.instance.writeValueAsString(onAir))
    mockMvc.perform(mvcDelete("/api/radio/2026-10-17")).andExpect(status().isConflict)
  }
}
