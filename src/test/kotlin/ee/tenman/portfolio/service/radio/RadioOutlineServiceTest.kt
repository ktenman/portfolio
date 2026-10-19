package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

private val SHOW = Show("Tarkade klubi", null, 0.0, 3900.0)

class RadioOutlineServiceTest {
  private val read =
    OutlineRead(
      listOf("Ainar Ruussaar"),
      ShowStart(5.0, 50.0, true, "Tere."),
      emptyList(),
      ShowEnd(90.0, "Kohtume taas."),
      listOf(OutlineChapter(50.0, "Rahvusooper Estonia")),
    )

  @Test
  fun `should ask for three reads with the outline schema`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(any(), any(), any(), any()) } returns JsonMapperFactory.instance.writeValueAsString(read)
    RadioOutlineService(client).reads(SHOW, "ärakiri", AiModel.GEMINI_3_5_FLASH_LITE) {}
    verify(exactly = 3) {
      client.complete(
        AiModel.GEMINI_3_5_FLASH_LITE,
        match { it.startsWith("The user message is a machine transcript") },
        "ärakiri",
        match { it["required"] == listOf("hosts", "showStart", "breaks", "showEnd", "chapters") },
      )
    }
  }

  @Test
  fun `should name the programme and the length of its window in the prompt`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(any(), any(), any(), any()) } returns null
    RadioOutlineService(client).reads(SHOW, "ärakiri", AiModel.GEMINI_3_5_FLASH_LITE) {}
    verify(exactly = 3) {
      client.complete(any(), match { "a 65-minute recording" in it && "the talk show \"Tarkade klubi\" is on air" in it }, any(), any())
    }
  }

  @Test
  fun `should keep a failed read as null`() {
    val client = mockk<OpenRouterClient>()
    val answer = JsonMapperFactory.instance.writeValueAsString(read)
    every { client.complete(any(), any(), any(), any()) } returnsMany listOf(answer, "{\"hosts\": ", answer)
    expect(RadioOutlineService(client).reads(SHOW, "ärakiri", AiModel.GEMINI_3_5_FLASH_LITE) {}).toEqual(listOf(read, null, read))
  }

  @Test
  fun `should report each read once it comes back, failed or not`() {
    val client = mockk<OpenRouterClient>()
    every { client.complete(any(), any(), any(), any()) } returnsMany listOf(null, "{}", null)
    var reported = 0
    RadioOutlineService(client).reads(SHOW, "ärakiri", AiModel.GEMINI_3_5_FLASH_LITE) { reported++ }
    expect(reported).toEqual(3)
  }
}
