package ee.tenman.portfolio.tuleva

import ch.tutteli.atrium.api.fluent.en_GB.asList
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.repository.FundAllocationRepository
import ee.tenman.portfolio.service.infrastructure.MinioService
import jakarta.annotation.Resource
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test
import org.wiremock.spring.InjectWireMock
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@IntegrationTest
class TulevaHoldingsServiceIT {
  @Resource
  private lateinit var tulevaHoldingsService: TulevaHoldingsService

  @Resource
  private lateinit var fundAllocationRepository: FundAllocationRepository

  @Resource
  private lateinit var minioService: MinioService

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  @Test
  fun `should store the fund weights of a newly published investment report`() {
    val url = publish(pdf())
    tulevaHoldingsService.importReports()
    expect(fundAllocationRepository.findBySourceUrl(url).map { "${it.asOfDate} ${it.underlyingIsin}=${it.weight}" })
      .toContainExactly("2026-08-31 IE00BFG1TM61=59.9000", "2026-08-31 IE00BKPTWY98=40.0000")
  }

  @Test
  fun `should archive the PDF of a newly published investment report`() {
    val report = pdf()
    publish(report)
    tulevaHoldingsService.importReports()
    expect(minioService.downloadFundReport(TulevaHoldingsService.SYMBOL, LocalDate.of(2026, 8, 31)))
      .notToEqualNull()
      .asList()
      .toEqual(report.asList())
  }

  private fun publish(report: ByteArray): String {
    wireMockServer.resetAll()
    fundAllocationRepository.deleteAll()
    val url = "${wireMockServer.baseUrl()}/wp-content/uploads/2026/09/aruanne-2026-08.pdf"
    wireMockServer.stubFor(
      get(urlPathEqualTo("/wp-json/wp/v2/media"))
        .withQueryParam("page", equalTo("1"))
        .withQueryParam("per_page", equalTo("100"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("X-WP-TotalPages", "1")
            .withBody("""[{"source_url":"$url"}]"""),
        ),
    )
    wireMockServer.stubFor(get(urlPathEqualTo("/wp-content/uploads/2026/09/aruanne-2026-08.pdf")).willReturn(aResponse().withBody(report)))
    return url
  }

  private fun pdf(): ByteArray =
    PDDocument().use { document ->
      val page = PDPage().also(document::addPage)
      PDPageContentStream(document, page).use { stream ->
        stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 8f)
        REPORT.lines().forEachIndexed { index, line ->
          stream.beginText()
          stream.newLineAtOffset(20f, 750f - index * 12)
          stream.showText(line)
          stream.endText()
        }
      }
      ByteArrayOutputStream().also(document::save).toByteArray()
    }

  companion object {
    private val REPORT =
      """
      Tuleva III Samba Pensionifond seisuga 31.08.2026
      BlackRock ISF Developed World IE00BFG1TM61 IE EUR 25.80 1 000 59.90%
      iShares Emerging Market Screened IE00BKPTWY98 IE EUR 10.18 1 000 40.00%
      Arvelduskonto AS SEB Pank EE EUR 100 100 0.10%
      AKTIVATE TURUVÄÄRTUS KOKKU 2 100 100.00%
      """.trimIndent()
  }
}
