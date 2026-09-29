package ee.tenman.portfolio.tuleva

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.LocalDate

class TulevaAllocationParserTest {
  @Test
  fun `should parse fund weights from the investment report`() {
    val report = TulevaAllocationParser.parse(AUGUST)
    expect(report.rows.map { "${it.isin}=${it.weight}" })
      .toContainExactly("IE00BFG1TM61=29.07", "IE000QWCYQT0=28.90", "IE000I9HGDZ3=29.36", "IE00BKPTWY98=12.55")
  }

  @Test
  fun `should parse the report date`() {
    expect(TulevaAllocationParser.parse(AUGUST).asOfDate).toEqual(LocalDate.of(2026, 8, 31))
  }

  @Test
  fun `should parse the reported asset total`() {
    expect(TulevaAllocationParser.parse(AUGUST).reportedTotal).toEqualNumerically(BigDecimal("100.02"))
  }

  @Test
  fun `should parse the date of the older bilingual report layout`() {
    val text = AUGUST.replace("seisuga 31.08.2026", "Investeeringute aruanne 30.09.2025 / Investment report 30.09.2025")
    expect(TulevaAllocationParser.parse(text).asOfDate).toEqual(LocalDate.of(2025, 9, 30))
  }

  @Test
  fun `should read the asset total from the next line when the label wraps`() {
    val text =
      AUGUST.replace(
      "AKTIVATE TURUVÄÄRTUS KOKKU  496 854 531  578 249 951 100.02%",
      "Aktivate turuväärtus kokku/ Total market\nvalue of assets 496 854 531 578 249 951 100.02%",
    )
    expect(TulevaAllocationParser.parse(text).reportedTotal).toEqualNumerically(BigDecimal("100.02"))
  }

  @Test
  fun `should repair a truncated ISIN that matches exactly one known fund`() {
    val text = AUGUST.replace("IE00BKPTWY98 IE EUR", "IE00BKPTWY9 IE EUR")
    val row = TulevaAllocationParser.parse(text).rows.last()
    expect("${row.isin} from ${row.repairedFrom}").toEqual("IE00BKPTWY98 from IE00BKPTWY9")
  }

  @Test
  fun `should accept a zero weight bank account row`() {
    val text =
      AUGUST.replace(
      "Arvelduskonto AS SEB Pank EE EUR   709 887   709 887 0.12%",
      "Arvelduskonto AS SEB Pank EE EUR   17   17 0.00%\nÜleöödeposiit Swedbank AS EE EUR   709 870   709 870 0.12%",
    )
    expect(TulevaAllocationParser.parse(text).rows.size).toEqual(4)
  }

  @Test
  fun `should accept an asset total above one hundred percent`() {
    val text =
      AUGUST
        .replace("0.12%\nHOIUSED", "3.66%\nHOIUSED")
        .replace("100.02%", "103.56%")
    expect(TulevaAllocationParser.parse(text).reportedTotal).toEqualNumerically(BigDecimal("103.56"))
  }

  @Test
  fun `should reject a negative weight`() {
    val text = AUGUST.replace("28.90%", "-28.90%").replace("0.12%\nHOIUSED", "57.92%\nHOIUSED")
    expect { TulevaAllocationParser.parse(text) }.toThrow<IllegalArgumentException>().messageToContain("-28.90")
  }

  @ParameterizedTest(name = "should reject a report where {0} becomes {1}")
  @CsvSource(
    delimiter = '|',
    value = [
      "31.08.2026 | 31.02.2026 | 31.02.2026",
      "seisuga 31.08.2026 | Tuleva | date",
      "IE000QWCYQT0 IE EUR | IE00BFG1TM61 IE EUR | Duplicate",
      "AKTIVATE TURUVÄÄRTUS KOKKU | KOKKU | total",
      "'Arvelduskonto AS SEB Pank EE EUR   709 887   709 887 0.12%' | '' | 99.90",
      "IE000QWCYQT0 IE EUR | ZZ000QWCYQT IE EUR | ZZ000QWCYQT",
      "IE000QWCYQT0 IE EUR | IE000QWCYQT1 IE EUR | IE000QWCYQT1",
      "' 28.90%' | '' | IE000QWCYQT0",
    ],
  )
  fun `should reject an invalid report`(
    from: String,
    to: String,
    message: String,
  ) {
    expect { TulevaAllocationParser.parse(AUGUST.replace(from, to)) }.toThrow<IllegalArgumentException>().messageToContain(message)
  }

  companion object {
    private const val AUGUST =
      """Tuleva III Samba Pensionifond
seisuga 31.08.2026
BlackRock ISF - Developed World ESG Screened Index BlackRock Asset Management Ireland Ltd IE00BFG1TM61 IE EUR 25.80  111 942 613 38.73  168 052 093 29.07%
Amundi MSCI World Screened UCITS ETF Amundi Ireland Limited IE000QWCYQT0 IE EUR 5.00  167 000 797 5.00  167 076 958 28.90%
Xtrackers MSCI World Screened UCITS ETF 1C DWS Investment S.A. IE000I9HGDZ3 IE EUR 10.00  168 572 345 10.07  169 749 205 29.36%
iShares Emerging Market Screened Equity Index Fund (IE) BlackRock Asset Management Ireland Ltd IE00BKPTWY98 IE EUR 10.18  48 502 322 15.22  72 535 241 12.55%
Aktsiafondid kokku  496 018 077  577 413 497 99.87% +0.07%
FONDIOSAKUD KOKKU  496 018 077  577 413 497 99.87% +0.07%
Muud nõuded EE EUR   126 566   126 566 0.02%
Arvelduskonto AS SEB Pank EE EUR   709 887   709 887 0.12%
HOIUSED KOKKU   836 454   836 454 0.14% -0.07%
AKTIVATE TURUVÄÄRTUS KOKKU  496 854 531  578 249 951 100.02%
Pärast aktivate kokku 50.00%"""
  }
}
