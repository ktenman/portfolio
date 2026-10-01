package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.FundReportDto
import ee.tenman.portfolio.service.etf.FundReportService
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDate

@RestController
@RequestMapping("/api/funds/{isin:[A-Z]{2}[A-Z0-9]{10}}/reports")
class FundReportController(
  private val minioService: MinioService,
  private val fundReportService: FundReportService,
) {
  @GetMapping
  fun getReports(
    @PathVariable isin: String,
  ): List<FundReportDto> = fundReportService.reports(isin)

  @GetMapping("/{asOfDate}")
  fun getReport(
    @PathVariable isin: String,
    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOfDate: LocalDate,
  ): ResponseEntity<ByteArray> =
    minioService.downloadFundReport(isin, asOfDate)?.let { pdf(isin, asOfDate, it) } ?: ResponseEntity.notFound().build()

  private fun pdf(
    isin: String,
    asOfDate: LocalDate,
    report: ByteArray,
  ): ResponseEntity<ByteArray> =
    ResponseEntity
      .ok()
      .contentType(MediaType.APPLICATION_PDF)
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition
        .inline()
        .filename("$isin-$asOfDate.pdf")
        .build()
        .toString(),
          ).cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
      .body(report)
}
