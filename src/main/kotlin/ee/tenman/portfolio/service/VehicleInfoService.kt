package ee.tenman.portfolio.service

import ee.tenman.portfolio.auto24.Auto24Service
import ee.tenman.portfolio.auto24.CarPriceResult
import ee.tenman.portfolio.configuration.TimeUtility
import ee.tenman.portfolio.dto.VehicleInfoResponse
import ee.tenman.portfolio.veego.VeegoResult
import ee.tenman.portfolio.veego.VeegoService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VehicleInfoService(
  private val auto24Service: Auto24Service,
  private val veegoService: VeegoService,
  private val calculationDispatcher: CoroutineDispatcher,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val trailingYear = Regex(""",\s*\d{4}$""")

  fun getVehicleInfo(plateNumber: String): VehicleInfoResponse {
    val normalized = plateNumber.uppercase()
    log.info("Fetching vehicle info for plate: $normalized")
    val startTime = System.nanoTime()
    val (auto24Result, veegoResult) = fetchInParallel(normalized)
    val totalDuration = TimeUtility.durationInSeconds(startTime).toDouble()
    log.info("Vehicle info fetched for $normalized in ${totalDuration}s")
    return buildResponse(normalized, auto24Result, veegoResult, totalDuration)
  }

  private fun fetchInParallel(plateNumber: String): Pair<CarPriceResult, VeegoResult> =
    runBlocking(calculationDispatcher) {
      val auto24Deferred = async { fetchAuto24Safely(plateNumber) }
      val veegoDeferred = async { veegoService.getTaxInfo(plateNumber) }
      Pair(auto24Deferred.await(), veegoDeferred.await())
    }

  private fun fetchAuto24Safely(plateNumber: String): CarPriceResult =
    runCatching { auto24Service.findCarPrice(plateNumber) }
      .getOrElse { exception ->
        log.error("Failed to fetch Auto24 price for $plateNumber: ${exception.message}")
        CarPriceResult(price = null, error = exception.message ?: "Unknown error")
      }

  private fun buildResponse(
    plateNumber: String,
    auto24Result: CarPriceResult,
    veegoResult: VeegoResult,
    totalDuration: Double,
  ): VehicleInfoResponse {
    val marketPrice = extractMarketPrice(auto24Result)
    return VehicleInfoResponse(
      plateNumber = plateNumber,
      marketPrice = marketPrice,
      annualTax = veegoResult.annualTax,
      registrationTax = veegoResult.registrationTax,
      make = veegoResult.make,
      model = veegoResult.model,
      year = veegoResult.year,
      group = veegoResult.group,
      co2 = veegoResult.co2,
      fuel = veegoResult.fuel,
      weight = veegoResult.weight,
      auto24Error = auto24Result.error,
      veegoError = veegoResult.error,
      totalDurationSeconds = totalDuration,
      formattedText = buildFormattedText(plateNumber, veegoResult, auto24Result, marketPrice),
    )
  }

  private fun buildFormattedText(
    plateNumber: String,
    veegoResult: VeegoResult,
    auto24Result: CarPriceResult,
    marketPrice: String?,
  ): String {
    val sb = StringBuilder()
    vehicleName(veegoResult, auto24Result)?.let { sb.append("🚗 $it ($plateNumber)\n\n") }
    sb.append("📋 Details:\n")
    veegoResult.fuel?.let { sb.append("• Engine: $it\n") }
    veegoResult.year?.let { sb.append("• First registration: $it\n") }
    veegoResult.co2?.let { sb.append("• CO2: $it\n") }
    veegoResult.weight?.let { sb.append("• Gross weight: $it\n") }
    sb.append("\n💰 Tax Information:\n")
    veegoResult.registrationTax?.let { sb.append("• Registration tax: $it€\n") }
    veegoResult.annualTax?.let { sb.append("• Yearly tax: $it€\n") }
    if (marketPrice != null) {
      sb.append("\n💵 Market Price:\n")
      sb.append("• Price: $marketPrice\n")
    }
    return sb.toString().trimEnd()
  }

  private fun vehicleName(
    veegoResult: VeegoResult,
    auto24Result: CarPriceResult,
  ): String? =
    auto24Result.vehicleName?.replace(trailingYear, "")
      ?: listOfNotNull(veegoResult.make, veegoResult.model).joinToString(" ").ifEmpty { null }

  private fun extractMarketPrice(result: CarPriceResult): String? = result.price?.replace(" kuni ", " to ")
}
