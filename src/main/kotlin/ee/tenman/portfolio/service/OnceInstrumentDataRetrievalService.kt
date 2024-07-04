package ee.tenman.portfolio.service

import ee.tenman.portfolio.job.InstrumentDataRetrievalJob
import ee.tenman.portfolio.service.xirr.XirrService
import jakarta.annotation.PostConstruct
import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
class OnceInstrumentDataRetrievalService(
  private val instrumentDataRetrievalJob: InstrumentDataRetrievalJob,
  private val xirrService: XirrService
) {

  companion object {
    private val log = LoggerFactory.getLogger(OnceInstrumentDataRetrievalService::class.java)
  }

  @PostConstruct
  fun retrieveData() {
    log.info("Retrieving data for all instruments")
    instrumentDataRetrievalJob.retrieveInstrumentData()
    xirrService.calculateStockXirr("QDVE.DEX")
    xirrService.calculateStockXirr("QDVE")
  }

}
