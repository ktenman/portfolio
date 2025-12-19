package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.auto24.Auto24Service
import ee.tenman.portfolio.auto24.CarPriceResult
import ee.tenman.portfolio.veego.VeegoResult
import ee.tenman.portfolio.veego.VeegoService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class VehicleInfoServiceTest {
  private val auto24Service = mockk<Auto24Service>()
  private val veegoService = mockk<VeegoService>()
  private lateinit var vehicleInfoService: VehicleInfoService

  @BeforeEach
  fun setUp() {
    vehicleInfoService = VehicleInfoService(auto24Service, veegoService, Dispatchers.Default)
  }

  @Test
  fun `should return combined vehicle info when both APIs succeed`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "3400 € kuni 8300 €", durationSeconds = 1.5)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.plateNumber).toEqual("876BCH")
    expect(result.marketPrice).toEqual("3400 € to 8300 €")
    expect(result.annualTax).notToEqualNull().toEqualNumerically(BigDecimal("94.14"))
    expect(result.registrationTax).notToEqualNull().toEqualNumerically(BigDecimal("599.50"))
    expect(result.make).toEqual("Subaru")
    expect(result.model).toEqual("Forester")
    expect(result.year).toEqual(2009)
    expect(result.auto24Error).toEqual(null)
    expect(result.veegoError).toEqual(null)
    expect(result.totalDurationSeconds).toBeGreaterThanOrEqualTo(0.0)
  }

  @Test
  fun `should return partial data when auto24 returns vehicle not found`() {
    val plateNumber = "999ZZZ"
    every { auto24Service.findCarPrice(plateNumber) } returns
      CarPriceResult(price = null, error = "Vehicle not found", durationSeconds = 0.5)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.plateNumber).toEqual("999ZZZ")
    expect(result.marketPrice).toEqual(null)
    expect(result.auto24Error).toEqual("Vehicle not found")
    expect(result.annualTax).notToEqualNull().toEqualNumerically(BigDecimal("94.14"))
    expect(result.veegoError).toEqual(null)
  }

  @Test
  fun `should return partial data when auto24 returns price not available`() {
    val plateNumber = "123ABC"
    every { auto24Service.findCarPrice(plateNumber) } returns
      CarPriceResult(price = null, error = "Price not available", durationSeconds = 0.3)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.marketPrice).toEqual(null)
    expect(result.auto24Error).toEqual("Price not available")
    expect(result.annualTax).notToEqualNull()
  }

  @Test
  fun `should return partial data when veego fails but auto24 succeeds`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns VeegoResult.error("Connection refused", 0.1)

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.plateNumber).toEqual("876BCH")
    expect(result.marketPrice).toEqual("5000 €")
    expect(result.auto24Error).toEqual(null)
    expect(result.annualTax).toEqual(null)
    expect(result.veegoError).toEqual("Connection refused")
  }

  @Test
  fun `should return errors when both APIs fail`() {
    val plateNumber = "INVALID"
    every { auto24Service.findCarPrice(plateNumber) } returns
      CarPriceResult(price = null, error = "Vehicle not found", durationSeconds = 0.5)
    every { veegoService.getTaxInfo(plateNumber) } returns VeegoResult.error("Not found", 0.2)

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.marketPrice).toEqual(null)
    expect(result.auto24Error).toEqual("Vehicle not found")
    expect(result.annualTax).toEqual(null)
    expect(result.veegoError).toEqual("Not found")
  }

  @Test
  fun `should handle auto24 exception gracefully`() {
    val plateNumber = "CRASH"
    every { auto24Service.findCarPrice(plateNumber) } throws RuntimeException("Failed to fetch price: timeout")
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.auto24Error).toEqual("Failed to fetch price: timeout")
    expect(result.marketPrice).toEqual(null)
    expect(result.annualTax).notToEqualNull()
  }

  @Test
  fun `should translate kuni to english in market price`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "1000 € kuni 2000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.marketPrice).toEqual("1000 € to 2000 €")
  }

  @Test
  fun `should not translate when no kuni present`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.marketPrice).toEqual("5000 €")
  }

  @Test
  fun `should include car emoji and make model in formatted text`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.formattedText).toContain("🚗 Subaru Forester")
  }

  @Test
  fun `should include details section in formatted text`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.formattedText).toContain("📋 Details:")
    expect(result.formattedText).toContain("• Type: Passenger car")
    expect(result.formattedText).toContain("• Engine: Petrol")
    expect(result.formattedText).toContain("• First registration: 2009")
    expect(result.formattedText).toContain("• CO2: 199")
    expect(result.formattedText).toContain("• Gross weight: 2015")
  }

  @Test
  fun `should include tax information in formatted text`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.formattedText).toContain("💰 Tax Information:")
    expect(result.formattedText).toContain("• Registration tax: 599.50€")
    expect(result.formattedText).toContain("• Yearly tax: 94.14€")
  }

  @Test
  fun `should include market price section in formatted text when price available`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.formattedText).toContain("💵 Market Price:")
    expect(result.formattedText).toContain("• Price: 5000 €")
  }

  @Test
  fun `should not include market price section when price not available`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns
      CarPriceResult(price = null, error = "Vehicle not found", durationSeconds = 0.5)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.formattedText.contains("💵 Market Price:")).toEqual(false)
  }

  @Test
  fun `should not include car header when make is null`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createVeegoResultWithoutMakeModel()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.formattedText.contains("🚗")).toEqual(false)
  }

  @Test
  fun `should call both services in parallel`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns CarPriceResult(price = "5000 €", durationSeconds = 1.0)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    vehicleInfoService.getVehicleInfo(plateNumber)

    verify(exactly = 1) { auto24Service.findCarPrice(plateNumber) }
    verify(exactly = 1) { veegoService.getTaxInfo(plateNumber) }
  }

  @Test
  fun `should propagate auto24 error to response`() {
    val plateNumber = "876BCH"
    every { auto24Service.findCarPrice(plateNumber) } returns
      CarPriceResult(price = null, error = "Internal Server Error", durationSeconds = 0.5)
    every { veegoService.getTaxInfo(plateNumber) } returns createSuccessfulVeegoResult()

    val result = vehicleInfoService.getVehicleInfo(plateNumber)

    expect(result.marketPrice).toEqual(null)
    expect(result.auto24Error).toEqual("Internal Server Error")
  }

  private fun createSuccessfulVeegoResult(): VeegoResult =
    VeegoResult(
      annualTax = BigDecimal("94.14"),
      registrationTax = BigDecimal("599.50"),
      make = "Subaru",
      model = "Forester",
      year = 2009,
      group = "Passenger car",
      co2 = 199,
      fuel = "Petrol",
      weight = 2015,
      error = null,
      durationSeconds = 0.5,
    )

  private fun createVeegoResultWithoutMakeModel(): VeegoResult =
    VeegoResult(
      annualTax = BigDecimal("94.14"),
      registrationTax = BigDecimal("599.50"),
      make = null,
      model = null,
      year = 2009,
      group = "Passenger car",
      co2 = 199,
      fuel = "Petrol",
      weight = 2015,
      error = null,
      durationSeconds = 0.5,
    )
}
