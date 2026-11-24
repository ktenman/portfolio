package ee.tenman.portfolio.domain

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class CachedEntitySerialVersionTest {
  @Test
  fun `should keep the serial version of a cached instrument`() {
    expect(declared(Instrument::class.java)).toEqual(5195459143216650820L)
  }

  @Test
  fun `should keep the serial version of a cached transaction`() {
    expect(declared(PortfolioTransaction::class.java)).toEqual(-5966201828531052108L)
  }

  @Test
  fun `should keep the serial version of a cached daily summary`() {
    expect(declared(PortfolioDailySummary::class.java)).toEqual(3196017946321380390L)
  }

  private fun declared(type: Class<*>): Long = type.getDeclaredField("serialVersionUID").apply { isAccessible = true }.getLong(null)
}
