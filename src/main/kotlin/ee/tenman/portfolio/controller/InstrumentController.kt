package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.dto.InstrumentDto
import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.FtDataRetrievalJob
import ee.tenman.portfolio.service.InstrumentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.cache.CacheManager
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instruments")
@Validated
@Tag(name = "Instruments", description = "APIs for managing financial instruments")
class InstrumentController(
  private val instrumentService: InstrumentService,
  private val binanceDataRetrievalJob: BinanceDataRetrievalJob?,
  private val ftDataRetrievalJob: FtDataRetrievalJob?,
  private val cacheManager: CacheManager,
  private val transactionService: ee.tenman.portfolio.service.TransactionService,
) {
  @PostMapping
  @Loggable
  @Operation(summary = "Create a new instrument")
  fun saveInstrument(
    @Valid @RequestBody instrumentDto: InstrumentDto,
  ): InstrumentDto {
    val savedInstrument = instrumentService.saveInstrument(instrumentDto.toEntity())
    return InstrumentDto.fromEntity(savedInstrument)
  }

  @GetMapping
  @Loggable
  fun getAllInstruments(
    @RequestParam(required = false) platforms: List<String>?,
  ): List<InstrumentDto> =
    instrumentService
      .getAllInstruments(platforms)
      .sortedBy { it.id }
      .map { InstrumentDto.fromEntity(it) }

  @PutMapping("/{id}")
  @Loggable
  @Operation(summary = "Update an existing instrument")
  fun updateInstrument(
    @PathVariable id: Long,
    @Valid @RequestBody instrumentDto: InstrumentDto,
  ): InstrumentDto {
    val existingInstrument = instrumentService.getInstrumentById(id)
    val updatedInstrument =
      existingInstrument.apply {
        symbol = instrumentDto.symbol
        name = instrumentDto.name
        category = instrumentDto.category
        baseCurrency = instrumentDto.baseCurrency
        currentPrice = instrumentDto.currentPrice
      }

    val savedInstrument = instrumentService.saveInstrument(updatedInstrument)
    return InstrumentDto.fromEntity(savedInstrument)
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteInstrument(
    @PathVariable id: Long,
  ) = instrumentService.deleteInstrument(id)

  @PostMapping("/refresh-prices")
  @Loggable
  @Operation(summary = "Refresh prices from Binance and FT providers")
  fun refreshPrices(): Map<String, String> {
    binanceDataRetrievalJob?.let { job ->
      CoroutineScope(Dispatchers.Default).launch {
        job.execute()
      }
    }

    ftDataRetrievalJob?.let { job ->
      CoroutineScope(Dispatchers.Default).launch {
        job.execute()
      }
    }

    listOf(INSTRUMENT_CACHE, SUMMARY_CACHE, TRANSACTION_CACHE).forEach { cacheName ->
      cacheManager.getCache(cacheName)?.clear()
    }

    CoroutineScope(Dispatchers.Default).launch {
      val allTransactions = transactionService.getAllTransactions()
      transactionService.calculateTransactionProfits(allTransactions)
    }

    return mapOf("status" to "Jobs triggered, caches cleared, and transaction profits recalculated")
  }
}
