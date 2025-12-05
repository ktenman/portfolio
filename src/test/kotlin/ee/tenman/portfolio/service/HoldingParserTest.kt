package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import com.codeborne.selenide.ElementsCollection
import com.codeborne.selenide.SelenideElement
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HoldingParserTest {
  private lateinit var holdingParser: HoldingParser
  private lateinit var mockElement: SelenideElement
  private lateinit var mockDivCollection: ElementsCollection

  @BeforeEach
  fun setUp() {
    holdingParser = HoldingParser()
    mockElement = mockk(relaxed = true)
    mockDivCollection = mockk(relaxed = true)
  }

  @Test
  fun `should parse holding row with valid data`() {
    val divTexts = listOf("Apple Inc\n\$AAPL\nTechnology", "5.25%")
    val mockImgCollection = mockk<ElementsCollection>(relaxed = true)
    every { mockElement.text() } returns "Apple Inc \$AAPL Technology 5.25%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts
    every { mockElement.findAll("img") } returns mockImgCollection
    every { mockImgCollection.firstOrNull() } returns null

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).notToEqualNull()
    expect(result!!.name).toEqual("Apple Inc")
    expect(result.ticker).toEqual("AAPL")
    expect(result.sector).toEqual("Technology")
    expect(result.weight).toEqualNumerically(BigDecimal("5.25"))
    expect(result.rank).toEqual(1)
  }

  @Test
  fun `should return null when holding has zero weight`() {
    val divTexts = listOf("Some Company\n\$XYZ\nFinance", "0%")
    every { mockElement.text() } returns "Some Company \$XYZ Finance 0%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).toEqual(null)
  }

  @Test
  fun `should handle instrument not available`() {
    val divTexts = listOf("Unknown Corp\n\$UNK\nUnknown", "Instrument is not available", "2.5%")
    val mockImgCollection = mockk<ElementsCollection>(relaxed = true)
    every { mockElement.text() } returns "Unknown Corp Instrument is not available 2.5%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts
    every { mockElement.findAll("img") } returns mockImgCollection
    every { mockImgCollection.firstOrNull() } returns null

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).notToEqualNull()
    expect(result!!.name).toEqual("Unknown Corp")
    expect(result.ticker).toEqual(null)
    expect(result.sector).toEqual(null)
  }

  @Test
  fun `should normalize weight greater than 100`() {
    val divTexts = listOf("Big Corp\n\$BIG\nIndustrial", "525%")
    val mockImgCollection = mockk<ElementsCollection>(relaxed = true)
    every { mockElement.text() } returns "Big Corp \$BIG Industrial 525%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts
    every { mockElement.findAll("img") } returns mockImgCollection
    every { mockImgCollection.firstOrNull() } returns null

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).notToEqualNull()
    expect(result!!.weight).toEqualNumerically(BigDecimal("52.5000"))
  }

  @Test
  fun `should return null when name parts are empty`() {
    every { mockElement.text() } returns ""
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns emptyList()

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).toEqual(null)
  }

  @Test
  fun `should reset state clears previous weight`() {
    val divTexts = listOf("First Corp\n\$FIRST\nTech", "5.0%")
    val mockImgCollection = mockk<ElementsCollection>(relaxed = true)
    every { mockElement.text() } returns "First Corp \$FIRST Tech 5.0%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts
    every { mockElement.findAll("img") } returns mockImgCollection
    every { mockImgCollection.firstOrNull() } returns null
    holdingParser.parseHoldingRow(mockElement, 1)

    holdingParser.resetState()

    val secondDivTexts = listOf("Second Corp\n\$SECOND\nFinance", "10.0%")
    every { mockElement.text() } returns "Second Corp \$SECOND Finance 10.0%"
    every { mockDivCollection.texts() } returns secondDivTexts
    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).notToEqualNull()
    expect(result!!.name).toEqual("Second Corp")
  }

  @Test
  fun `should handle exception during parsing gracefully`() {
    every { mockElement.text() } returns "Valid text"
    every { mockElement.findAll("div") } throws RuntimeException("Parse error")

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).toEqual(null)
  }

  @Test
  fun `should skip weight values exceeding 10000 percent`() {
    val divTexts = listOf("Huge Corp\n\$HUGE\nFinance", "15000%")
    every { mockElement.text() } returns "Huge Corp \$HUGE Finance 15000%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).toEqual(null)
  }

  @Test
  fun `should handle weight with comma separator`() {
    val divTexts = listOf("European Corp\n\$EUR\nFinance", "1,234%")
    val mockImgCollection = mockk<ElementsCollection>(relaxed = true)
    every { mockElement.text() } returns "European Corp \$EUR Finance 1,234%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts
    every { mockElement.findAll("img") } returns mockImgCollection
    every { mockImgCollection.firstOrNull() } returns null

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).notToEqualNull()
    expect(result!!.weight).toEqualNumerically(BigDecimal("12.3400"))
  }

  @Test
  fun `should extract background image url when img src not available`() {
    val divTexts = listOf("Tesla\n\$TSLA\nAutomotive", "3.2%")
    val mockDiv = mockk<SelenideElement>(relaxed = true)
    val mockImgCollection = mockk<ElementsCollection>(relaxed = true)
    every { mockElement.text() } returns "Tesla \$TSLA Automotive 3.2%"
    every { mockElement.findAll("div") } returns mockDivCollection
    every { mockDivCollection.texts() } returns divTexts
    every { mockElement.findAll("img") } returns mockImgCollection
    every { mockImgCollection.firstOrNull() } returns null
    every { mockDivCollection.iterator() } returns mutableListOf(mockDiv).iterator()
    every { mockDiv.getAttribute("style") } returns "background-image: url('https://example.com/bg-logo.png')"

    val result = holdingParser.parseHoldingRow(mockElement, 1)

    expect(result).notToEqualNull()
  }
}
