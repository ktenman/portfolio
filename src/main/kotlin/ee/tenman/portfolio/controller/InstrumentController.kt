package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.dto.InstrumentDto
import ee.tenman.portfolio.dto.InstrumentsResponse
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.integration.IndustryClassificationService
import ee.tenman.portfolio.service.pricing.PriceRefreshService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
  private val priceRefreshService: PriceRefreshService,
  private val industryClassificationService: IndustryClassificationService,
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
    @RequestParam(defaultValue = "24h") period: String,
  ): InstrumentsResponse {
    val result = instrumentService.getAllInstrumentSnapshotsWithPortfolioXirr(platforms, period)
    val instruments = result.snapshots.sortedBy { it.instrument.id }.map { InstrumentDto.fromSnapshot(it) }
    return InstrumentsResponse(instruments = instruments, portfolioXirr = result.portfolioXirr)
  }

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
  @Operation(summary = "Refresh prices from Binance and Lightyear providers")
  fun refreshPrices(): Map<String, String> = mapOf("status" to priceRefreshService.refreshAllPrices())

  @GetMapping("/classify-industry")
  @Operation(summary = "Test industry classification for a company")
  fun classifyCompanyIndustry(
    @RequestParam companyName: String,
  ): Map<String, String?> {
    val sector = industryClassificationService.classifyCompany(companyName)
    return mapOf(
      "companyName" to companyName,
      "industrySector" to sector?.displayName,
      "sectorCode" to sector?.name,
    )
  }

  @PostMapping("/classify-etf-holdings")
  @Operation(summary = "Trigger ETF holdings classification job")
  fun triggerEtfHoldingsClassification(): Map<String, String> = mapOf("status" to priceRefreshService.triggerEtfHoldingsClassification())
}
