package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.service.MinioService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class LogoUploadRunner(
  private val minioService: MinioService,
) : ApplicationRunner {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun run(args: ApplicationArguments) {
    uploadLogoIfMissing("BTC")
  }

  private fun uploadLogoIfMissing(ticker: String) {
    log.info("Checking logo for ticker: {}", ticker)

    if (minioService.logoExists(ticker)) {
      log.info("Logo already exists in MinIO for ticker: {}", ticker)
      return
    }

    try {
      val resource = ClassPathResource("static/logos/$ticker.png")
      log.info("Looking for logo file: static/logos/{}.png, exists: {}", ticker, resource.exists())

      if (!resource.exists()) {
        log.warn("Logo file not found in static resources for ticker: {}", ticker)
        return
      }

      val logoData = resource.inputStream.use { it.readBytes() }
      log.info("Read {} bytes from logo file for ticker: {}", logoData.size, ticker)

      minioService.uploadLogo(ticker, logoData)
      log.info("Successfully uploaded logo to MinIO for ticker: {}", ticker)

      val exists = minioService.logoExists(ticker)
      log.info("Verification after upload - logo exists: {} for ticker: {}", exists, ticker)
    } catch (e: Exception) {
      log.error("Failed to upload logo to MinIO for ticker: {}", ticker, e)
    }
  }
}
