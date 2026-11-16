package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.AiPurpose
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RadioDigestModelServiceTest {
  @Test
  fun `should ask the digest purpose with the judge schema`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(AiPurpose.RADIO_DIGEST, any(), any(), any(), any()) } returns null
    RadioDigestModelService(client, RadioProperties()).judge(listOf(ROW), listOf(LocalDate.of(2026, 11, 15) to "Telia koondamised"))
    verify {
      client.complete(
        AiPurpose.RADIO_DIGEST,
        AiModel.GEMINI_4_CARBON,
        match { "at most 30 stories" in it },
        "r1 | Sat 14.11 | Keskpäevatund (Kuku) | 6.5 min | Rünnakud Ust-Lugas | Droonid tabasid sadamat.\n\n" +
          "Told before:\n15.11 Telia koondamised",
        match { it["required"] == listOf("stories") },
      )
    }
  }

  @Test
  fun `should read the stories the judge returns`() {
    val client = mockk<OpenRouterClient>()
    val judgement = Judgement(listOf(STORY))
    every { client.complete(AiPurpose.RADIO_DIGEST, any(), any(), any(), any()) } returns
      JsonMapperFactory.instance.writeValueAsString(judgement)
    expect(RadioDigestModelService(client, RadioProperties()).judge(listOf(ROW), emptyList())).toEqual(judgement)
  }

  @Test
  fun `should return no judgement when the answer is not json`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(AiPurpose.RADIO_DIGEST, any(), any(), any(), any()) } returns "Vabandust, ei saa."
    expect(RadioDigestModelService(client, RadioProperties()).judge(listOf(ROW), emptyList())).toEqual(null)
  }

  @Test
  fun `should number the lines it sends for a clip`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(AiPurpose.RADIO_DIGEST, any(), any(), any(), any()) } returns null
    val lines =
      listOf(
        TranscriptLine(600.0, 620.0, "Sõda on lähedal.", emptyList()),
        TranscriptLine(630.5, 650.0, "Öeldi nii.", emptyList()),
      )
    RadioDigestModelService(client, RadioProperties()).clip(STORY, lines)
    verify {
      client.complete(
        AiPurpose.RADIO_DIGEST,
        AiModel.GEMINI_4_CARBON,
        match { "1.5 to 4 minutes" in it },
        "Ust-Luga\nSõda jõudis lähedale.\n\n1 | 0:00 | Sõda on lähedal.\n2 | 0:30 | Öeldi nii.",
        match { it["required"] == listOf("first", "last") },
      )
    }
  }

  @Test
  fun `should read the first and last line of a clip`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(AiPurpose.RADIO_DIGEST, any(), any(), any(), any()) } returns """{"first":2,"last":5}"""
    expect(RadioDigestModelService(client, RadioProperties()).clip(STORY, emptyList())).toEqual(ClipAnswer(2, 5))
  }

  companion object {
    private val ROW =
      DigestRow(
        "r1",
        "2026-11-14-1200-keskpaevatund",
        "Keskpäevatund",
        Station.KUKU,
        LocalDate.of(2026, 11, 14),
        "Rünnakud Ust-Lugas",
        "Droonid tabasid sadamat.",
        600.0,
        1000.0,
        390.0,
      )
    private val STORY = JudgedStory("Ust-Luga", "Sõda jõudis lähedale.", listOf("r1"), "r1", "news", 5, 4, false, "Julgeolek")
  }
}
