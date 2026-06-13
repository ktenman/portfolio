package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class AvivaPensionTransactionsMigrationTest {
  private val sql =
    requireNotNull(javaClass.getResourceAsStream("/db/migration/V202606111010__aviva_pension_transactions.sql"))
      .bufferedReader()
      .use { it.readText() }

  @Test
  fun `should buy aviva units twenty three times`() {
    val buys = Regex("'BUY'").findAll(sql).count()
    expect(buys).toEqual(23)
  }

  @Test
  fun `should model six annual fee sells at near zero price`() {
    val fees = Regex("'SELL', [0-9.]+, 0\\.00000001, ").findAll(sql).count()
    expect(fees).toEqual(6)
  }

  @Test
  fun `should record every transaction on the aviva platform`() {
    val transactions = Regex("'(BUY|SELL)'").findAll(sql).count()
    val avivaRows = Regex("'AVIVA'").findAll(sql).count()
    expect(avivaRows).toEqual(transactions)
  }
}
