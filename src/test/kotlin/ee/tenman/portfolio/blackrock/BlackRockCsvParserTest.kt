package ee.tenman.portfolio.blackrock

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BlackRockCsvParserTest {
  private val header =
    "Ticker,Name,Sector,Asset Class,Market Value,Weight (%),Notional Value,Shares,Price,Location,Exchange,Market Currency"

  private fun csv(vararg rows: String): String =
    listOf("Fund Holdings as of,\"11/Jun/2026\"", " ", header, *rows).joinToString("\n")

  @Test
  fun `should parse equity holdings skipping preamble`() {
    val csv =
      csv(
        "\"NVDA\",\"NVIDIA CORP\",\"Information Technology\",\"Equity\",\"351,722,454.96\",\"7.42\"," +
          "\"351,722,454.96\",\"1,716,808.00\",\"204.87\",\"United States\",\"NASDAQ\",\"USD\"",
        "\"AAPL\",\"APPLE INC\",\"Information Technology\",\"Equity\",\"322,775,337.86\",\"6.81\"," +
          "\"322,775,337.86\",\"1,091,822.00\",\"295.63\",\"United States\",\"NASDAQ\",\"USD\"",
      )

    val holdings = BlackRockCsvParser.parse(csv)

    expect(holdings).toHaveSize(2)
    expect(holdings.first().ticker).toEqual("NVDA")
    expect(holdings.first().name).toEqual("NVIDIA CORP")
    expect(holdings.first().sector).toEqual("Information Technology")
    expect(holdings.first().weight).toEqualNumerically(BigDecimal("7.42"))
  }

  @Test
  fun `should skip non equity rows`() {
    val csv =
      csv(
        "\"NVDA\",\"NVIDIA CORP\",\"Information Technology\",\"Equity\",\"1\",\"7.42\",\"1\",\"1\"," +
          "\"204.87\",\"United States\",\"NASDAQ\",\"USD\"",
        "\"USD\",\"US DOLLAR\",\"Cash and/or Derivatives\",\"Cash\",\"1\",\"0.20\",\"1\",\"1\"," +
          "\"1.00\",\"United States\",\"-\",\"USD\"",
      )

    val holdings = BlackRockCsvParser.parse(csv)

    expect(holdings).toHaveSize(1)
    expect(holdings.first().ticker).toEqual("NVDA")
  }

  @Test
  fun `should skip malformed rows without losing the feed`() {
    val csv =
      csv(
        "\"NVDA\",\"NVIDIA CORP\",\"Information Technology\",\"Equity\",\"1\",\"7.42\",\"1\",\"1\"," +
          "\"204.87\",\"United States\",\"NASDAQ\",\"USD\"",
        "\"BAD\",\"BAD ROW\",\"Information Technology\",\"Equity\",\"1\",\"not-a-number\",\"1\",\"1\"," +
          "\"1.00\",\"United States\",\"NASDAQ\",\"USD\"",
      )

    val holdings = BlackRockCsvParser.parse(csv)

    expect(holdings).toHaveSize(1)
    expect(holdings.first().ticker).toEqual("NVDA")
  }

  @Test
  fun `should throw when ticker header row is absent`() {
    val csv = "Fund Holdings as of,\"11/Jun/2026\"\nsome,unexpected,content"

    expect {
      BlackRockCsvParser.parse(csv)
    }.toThrow<IllegalStateException>().messageToContain("Ticker")
  }
}
