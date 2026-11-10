package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiPurpose
import ee.tenman.portfolio.domain.OpenRouterCall
import ee.tenman.portfolio.repository.OpenRouterCallRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OpenRouterSpendServiceTest {
  private val repository = mockk<OpenRouterCallRepository>()

  private val clock = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneId.of("Europe/Tallinn"))

  private fun saved(): OpenRouterCall {
    val call = slot<OpenRouterCall>()
    every { repository.save(capture(call)) } answers { firstArg() }
    OpenRouterSpendService(repository, clock).record(AiPurpose.LOGO_SELECTION, "google/gemini-3.5-flash-lite", 0.00000012)
    return call.captured
  }

  @Test
  fun `saves the cost under the day it was made in Tallinn`() {
    expect(saved().day).toEqual(LocalDate.of(2026, 11, 1))
  }

  @Test
  fun `saves the purpose under the label its metrics carry`() {
    expect(saved().purpose).toEqual("logo_selection")
  }

  @Test
  fun `keeps the call going when the cost cannot be saved`() {
    every { repository.save(any<OpenRouterCall>()) } throws IllegalStateException("Ühendus andmebaasiga katkes")
    expect { OpenRouterSpendService(repository, clock).record(AiPurpose.SECTOR, "google/gemini-4-argon", 0.0123) }.notToThrow()
  }
}
