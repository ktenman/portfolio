package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
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

  @Test
  fun `should parse Swiss Franc currency pattern with middle dot separator`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Swiss Prime Site AG Fr. SPSN·Real Estate</div>
          <div></div>
          <div></div>
          <div>1.5%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("Swiss Prime Site AG")
    expect(holdings[0].ticker).notToEqualNull().toEqual("SPSN")
    expect(holdings[0].sector).notToEqualNull().toEqual("Real Estate")
    expect(holdings[0].weight).toEqualNumerically(BigDecimal("1.5"))
  }

  @Test
  fun `should parse Danish Krone currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Danske Bank A/S DKr DANSKE·Finance</div>
          <div></div>
          <div></div>
          <div>2.0%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("Danske Bank A/S")
    expect(holdings[0].ticker).notToEqualNull().toEqual("DANSKE")
    expect(holdings[0].sector).notToEqualNull().toEqual("Finance")
  }

  @Test
  fun `should parse Swedish Krona currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Sandvik AB Skr SAND·Industrials</div>
          <div></div>
          <div></div>
          <div>0.8%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("Sandvik AB")
    expect(holdings[0].ticker).notToEqualNull().toEqual("SAND")
    expect(holdings[0].sector).notToEqualNull().toEqual("Industrials")
  }

  @Test
  fun `should parse Norwegian Krone currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Equinor ASA Nkr EQNR·Energy</div>
          <div></div>
          <div></div>
          <div>1.2%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("Equinor ASA")
    expect(holdings[0].ticker).notToEqualNull().toEqual("EQNR")
    expect(holdings[0].sector).notToEqualNull().toEqual("Energy")
  }

  @Test
  fun `should parse Hungarian Forint currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>OTP Bank Nyrt. Ft OTP·Finance</div>
          <div></div>
          <div></div>
          <div>0.5%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("OTP Bank Nyrt.")
    expect(holdings[0].ticker).notToEqualNull().toEqual("OTP")
    expect(holdings[0].sector).notToEqualNull().toEqual("Finance")
  }

  @Test
  fun `should parse Polish Zloty currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>PKO Bank Polski S.A zł PKO·Finance</div>
          <div></div>
          <div></div>
          <div>0.3%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("PKO Bank Polski S.A")
    expect(holdings[0].ticker).notToEqualNull().toEqual("PKO")
    expect(holdings[0].sector).notToEqualNull().toEqual("Finance")
  }

  @Test
  fun `should parse multi-word sector in European currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Temenos AG Fr. TEMN·Software & Cloud Services</div>
          <div></div>
          <div></div>
          <div>0.4%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("Temenos AG")
    expect(holdings[0].ticker).notToEqualNull().toEqual("TEMN")
    expect(holdings[0].sector).notToEqualNull().toEqual("Software & Cloud Services")
  }

  @Test
  fun `should parse company name with special characters in European currency pattern`() {
    val sampleHtml =
      """
      <!DOCTYPE html>
      <html>
      <body>
        <div class="table-row">
          <div>Chocoladefabriken Lindt & Sprüngli AG Fr. LISN·Consumer Essentials</div>
          <div></div>
          <div></div>
          <div>0.2%</div>
        </div>
      </body>
      </html>
      """.trimIndent()

    val holdings = lightyearHoldingsService.parseHoldings(sampleHtml, 1)

    expect(holdings.size).toEqual(1)
    expect(holdings[0].name).toEqual("Chocoladefabriken Lindt & Sprüngli AG")
    expect(holdings[0].ticker).notToEqualNull().toEqual("LISN")
    expect(holdings[0].sector).notToEqualNull().toEqual("Consumer Essentials")
  }
}
