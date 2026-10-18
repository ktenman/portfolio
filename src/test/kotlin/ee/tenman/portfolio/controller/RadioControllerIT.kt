package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.service.radio.EditList
import ee.tenman.portfolio.service.radio.KeptSpan
import ee.tenman.portfolio.service.radio.OutlineSummary
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RecordingPart
import ee.tenman.portfolio.service.radio.RecordingStatus
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_RANGE
import org.springframework.http.HttpHeaders.RANGE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put as mvcPut

@IntegrationTest
@TestPropertySource(properties = ["radio.recordings=\${java.io.tmpdir}/radio-controller-it"])
class RadioControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var radioProperties: RadioProperties

  private lateinit var directory: Path

  @BeforeEach
  fun setup() {
    radioProperties.recordings.toFile().deleteRecursively()
    directory = Files.createDirectories(radioProperties.recordings.resolve("2026-10-17"))
    val status = RecordingStatus("captured", 1792230900.0, 4200.0, listOf(RecordingPart("raw.mp3", 0.0)))
    Files.writeString(directory.resolve("status.json"), JsonMapperFactory.instance.writeValueAsString(status))
    Files.writeString(directory.resolve("episode.m4a"), "Keskpäevatund õ")
  }

  @Test
  fun `should list the recordings`() {
    mockMvc.perform(mvcGet("/api/radio")).andExpect(status().isOk).andExpect(jsonPath("$[0].name").value("2026-10-17"))
  }

  @Test
  fun `should serve a byte range of the episode`() {
    mockMvc
      .perform(mvcGet("/api/radio/2026-10-17/episode.m4a").header(RANGE, "bytes=0-3"))
      .andExpect(status().isPartialContent)
      .andExpect(header().string(CONTENT_RANGE, "bytes 0-3/17"))
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
  fun `should reject cuts that leave no audio`() {
    val outline = OutlineSummary(null, 1, 1, 1)
    val edit = EditList(listOf(KeptSpan(761.5, 3837.38)), 3075.88, emptyList(), emptyList(), emptyList(), outline, emptyList(), false, "")
    Files.writeString(directory.resolve("edit-list.json"), JsonMapperFactory.instance.writeValueAsString(edit))
    mockMvc
      .perform(mvcPut("/api/radio/2026-10-17/cuts").contentType(APPLICATION_JSON).content("""[{"start":20.0,"end":10.0}]"""))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `should not mark a missing transcript checked`() {
    mockMvc.perform(mvcPut("/api/radio/2026-10-17/transcript/checked")).andExpect(status().isNotFound)
  }
}
