package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import org.junit.jupiter.api.Test

class EtfBreakdownJsonServiceTest {
  private fun service() = EtfBreakdownJsonService(mockk(), mockk(), mockk())

  @Test
  fun `should give symbols in any order the same breakdown key`() {
    expect(service().breakdownKey(listOf("VUAA:GER:EUR", "Ödü"), listOf("LIGHTYEAR"))).toEqual(
      service().breakdownKey(listOf("Ödü", "VUAA:GER:EUR"), listOf("LIGHTYEAR")),
    )
  }

  @Test
  fun `should give empty and missing filters the same breakdown key`() {
    expect(service().breakdownKey(emptyList(), emptyList())).toEqual(service().breakdownKey(null, null))
  }

  @Test
  fun `should give different platforms different breakdown keys`() {
    expect(service().breakdownKey(null, listOf("LIGHTYEAR"))).notToEqual(service().breakdownKey(null, listOf("TRADING212")))
  }
}
