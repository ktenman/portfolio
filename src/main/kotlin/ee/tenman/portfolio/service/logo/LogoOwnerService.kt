package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.instrument.InstrumentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogoOwnerService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val instrumentRepository: InstrumentRepository,
  private val instrumentService: InstrumentService,
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun searchQuery(uuid: UUID): String? =
    etfHoldingRepository.findByUuid(uuid)?.let { LogoSearchQueryBuilder.buildQuery(it.name, it.ticker) }
      ?: instrumentRepository.findByUuid(uuid)?.let { LogoSearchQueryBuilder.buildQuery(it.issuer(), null) }

  fun ticker(uuid: UUID): String? = etfHoldingRepository.findByUuid(uuid)?.ticker

  fun saveManualLogo(
    uuid: UUID,
    image: ByteArray,
  ): Boolean {
    val holding = etfHoldingRepository.findByUuid(uuid) ?: return saveInstrumentLogo(uuid, image)
    minioService.uploadLogo(uuid, image)
    holding.logoSource = LogoSource.MANUAL
    etfHoldingRepository.save(holding)
    log.info("Successfully replaced logo for holding: ${holding.name}")
    return true
  }

  private fun saveInstrumentLogo(
    uuid: UUID,
    image: ByteArray,
  ): Boolean {
    val instrument = instrumentRepository.findByUuid(uuid)
    if (instrument == null) {
      log.warn("No holding or instrument found for UUID: $uuid")
      return false
    }
    minioService.uploadLogo(uuid, image)
    instrumentService.updateLogoSource(instrument.id, instrument.symbol, LogoSource.MANUAL)
    log.info("Successfully replaced logo for instrument: ${instrument.symbol}")
    return true
  }
}
