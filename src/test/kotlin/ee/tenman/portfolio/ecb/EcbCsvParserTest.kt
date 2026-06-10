package ee.tenman.portfolio.ecb

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class EcbCsvParserTest {
  private val header =
    "KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,OBS_STATUS,OBS_CONF,OBS_PRE_BREAK," +
      "OBS_COM,TIME_FORMAT,BREAKS,COLLECTION,COMPILING_ORG,DISS_ORG,DOM_SER_IDS,PUBL_ECB,PUBL_MU,PUBL_PUBLIC," +
      "UNIT_INDEX_BASE,COMPILATION,COVERAGE,DECIMALS,NAT_TITLE,SOURCE_AGENCY,SOURCE_PUB,TITLE,TITLE_COMPL,UNIT,UNIT_MULT"

  private fun row(
    date: String,
    value: String,
  ): String =
    "EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,$date,$value,A,F,,,P1D,,A,,,,,,,99Q1=100,,,5,,4F0,," +
      "Pound sterling/Euro ECB reference exchange rate," +
      "\"ECB reference exchange rate, Pound sterling/Euro, 2.15 pm (C.E.T.)\",GBP,0"

  @Test
  fun `should parse daily rates from ecb csv`() {
    val csv = listOf(header, row("2026-06-09", "0.8634"), row("2026-06-10", "0.86228")).joinToString("\n")

    val rates = EcbCsvParser.parse(csv)

    expect(rates).toContainExactly(
      EcbDailyRate(LocalDate.of(2026, 6, 9), BigDecimal("0.8634")),
      EcbDailyRate(LocalDate.of(2026, 6, 10), BigDecimal("0.86228")),
    )
  }

  @Test
  fun `should return empty list when csv is blank`() {
    val rates = EcbCsvParser.parse("")

    expect(rates).toBeEmpty()
  }

  @Test
  fun `should skip rows with blank observation value`() {
    val csv = listOf(header, row("2026-06-09", ""), row("2026-06-10", "0.86228")).joinToString("\n")

    val rates = EcbCsvParser.parse(csv)

    expect(rates).toContainExactly(EcbDailyRate(LocalDate.of(2026, 6, 10), BigDecimal("0.86228")))
  }

  @Test
  fun `should skip rows with malformed date`() {
    val csv = listOf(header, row("not-a-date", "0.8634"), row("2026-06-10", "0.86228")).joinToString("\n")

    val rates = EcbCsvParser.parse(csv)

    expect(rates).toContainExactly(EcbDailyRate(LocalDate.of(2026, 6, 10), BigDecimal("0.86228")))
  }
}
