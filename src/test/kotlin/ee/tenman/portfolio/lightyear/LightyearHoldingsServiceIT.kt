package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.math.BigDecimal

@IntegrationTest
class LightyearHoldingsServiceIT {
  @Resource
  private lateinit var lightyearHoldingsService: LightyearHoldingsService

  @Test
  @EnabledIfEnvironmentVariable(named = "LIGHTYEAR_HTTP_TEST", matches = "true")
  fun `should fetch and parse VUAA holdings from page 1`() {
    val holdings = lightyearHoldingsService.fetchHoldings("VUAA:XETRA", 1)

    expect(holdings.size).toBeGreaterThan(40)

    val firstHolding = holdings.first()
    expect(firstHolding.weight).toBeGreaterThan(BigDecimal.ZERO)
    expect(firstHolding.rank).toEqual(1)
  }

  @Test
  fun `should handle holdings with high weight values requiring normalization`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Test Company</div>
          <div>TEST</div>
          <div>Tech</div>
          <div>1234%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].weight).toEqualNumerically(BigDecimal("12.34"))
  }

  @Test
  fun `should skip holdings with zero weight`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Zero Weight Co</div>
          <div>ZERO</div>
          <div>Tech</div>
          <div>0%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(0)
  }

  @Test
  fun `should handle page 2 with correct rank offset`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Company A</div>
          <div>A</div>
          <div>Tech</div>
          <div>1.5%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 2)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].rank).toEqual(46)
  }

  @Test
  fun `should extract logo URL from img src`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>
            <img src="https://example.com/logo.png" />
            Company Name
          </div>
          <div>TICK</div>
          <div>Sector</div>
          <div>2.5%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].logoUrl).toEqual("https://example.com/logo.png")
  }

  @Test
  fun `should extract logo URL from background-image style`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div style="background-image: url('https://example.com/bg-logo.png')">Company Name</div>
          <div>TICK</div>
          <div>Sector</div>
          <div>3.0%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].logoUrl).toEqual("https://example.com/bg-logo.png")
  }
}
