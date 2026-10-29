package ee.tenman.portfolio.service.split

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SplitAnswersTest {
  private val today = LocalDate.of(2026, 10, 5)

  private val symbols = setOf("BNKE:PAR:EUR", "VUAA:GER:EUR")

  private val none =
    """{"symbol":"VUAA:GER:EUR","announced":false,"ratio":null,"effectiveDate":null,"sourceUrl":null,"publishedDate":null}"""

  private fun split(
    ratio: String? = "\"1:80\"",
    effective: String? = "\"2026-10-27\"",
    source: String? = "\"https://www.investegate.co.uk/announcement/9804833\"",
  ): String =
    """{"symbol":"BNKE:PAR:EUR","announced":true,"ratio":$ratio,"effectiveDate":$effective,"sourceUrl":$source,"publishedDate":"2026-10-02"}"""

  @Test
  fun `should read the split announced for an instrument`() {
    expect(parseSplits("[${split()},$none]", symbols, today)).toContainExactly(
      AnnouncedSplit(
        "BNKE:PAR:EUR",
        "1:80",
        LocalDate.of(2026, 10, 27),
        "https://www.investegate.co.uk/announcement/9804833",
        LocalDate.of(2026, 10, 2),
      ),
    )
  }

  @Test
  fun `should read no split when no instrument has one announced`() {
    val answer = """[${none.replace("VUAA:GER:EUR", "BNKE:PAR:EUR")},$none]"""
    expect(parseSplits(answer, symbols, today)).toBeEmpty()
  }

  @Test
  fun `should read an answer wrapped in a code fence`() {
    expect(parseSplits("```json\n[${split()},$none]\n```", symbols, today).map { it.ratio }).toContainExactly("1:80")
  }

  @Test
  fun `should skip a split that took effect before today`() {
    expect(parseSplits("[${split(effective = "\"2026-10-04\"")},$none]", symbols, today)).toBeEmpty()
  }

  @Test
  fun `should fail when an instrument is missing from the answer`() {
    expect { parseSplits("[${split()}]", symbols, today) }.toThrow<IllegalStateException>().messageToContain("VUAA:GER:EUR")
  }

  @Test
  fun `should fail when the answer names an instrument it was not asked about`() {
    val answer = "[${split()},$none,${none.replace("VUAA:GER:EUR", "LSMC:GER:EUR")}]"
    expect { parseSplits(answer, symbols, today) }.toThrow<IllegalStateException>().messageToContain("LSMC:GER:EUR")
  }

  @Test
  fun `should fail when an instrument is answered twice`() {
    expect { parseSplits("[${split()},$none,$none]", symbols, today) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should fail when the ratio is not two whole numbers`() {
    expect { parseSplits("[${split(ratio = "\"kaheksakümmend\"")},$none]", symbols, today) }
      .toThrow<IllegalStateException>()
      .messageToContain("kaheksakümmend")
  }

  @Test
  fun `should fail when an announced split has no effective date`() {
    expect { parseSplits("[${split(effective = null)},$none]", symbols, today) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should fail when an announced split has no web address for its source`() {
    expect { parseSplits("[${split(source = "\"investegate\"")},$none]", symbols, today) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should fail when an answer does not say whether a split is announced`() {
    val answer = """[${split()},{"symbol":"VUAA:GER:EUR"}]"""
    expect { parseSplits(answer, symbols, today) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should fail when the effective date is not a date`() {
    expect { parseSplits("[${split(effective = "\"27.10.2026\"")},$none]", symbols, today) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should fail when the answer holds no json array`() {
    expect { parseSplits("Ühtegi jagunemist ei leitud.", symbols, today) }.toThrow<IllegalStateException>()
  }
}
