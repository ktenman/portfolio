package ee.tenman.portfolio.veego

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class VeegoServiceTest {
  private val veegoClient = mockk<VeegoClient>()
  private lateinit var veegoService: VeegoService

  @BeforeEach
  fun setUp() {
    veegoService = VeegoService(veegoClient)
  }

  @Test
  fun `should return tax info when API call succeeds`() {
    val plateNumber = "876BCH"
    val response =
      VeegoTaxResponse(
      annualTax = BigDecimal("94.14"),
      registrationTax = BigDecimal("599.50"),
      make = "Subaru",
      model = "Forester",
      year = 2009,
      group = "Passenger car",
      co2 = 199,
      fuel = "Petrol",
      weight = 2015,
    )
    every { veegoClient.getTaxInfo(plateNumber, VeegoTaxRequest(plateNumber)) } returns response

    val result = veegoService.getTaxInfo(plateNumber)

    expect(result.annualTax).notToEqualNull().toEqualNumerically(BigDecimal("94.14"))
    expect(result.registrationTax).notToEqualNull().toEqualNumerically(BigDecimal("599.50"))
    expect(result.make).toEqual("Subaru")
    expect(result.model).toEqual("Forester")
    expect(result.year).toEqual(2009)
    expect(result.group).toEqual("Passenger car")
    expect(result.co2).toEqual(199)
    expect(result.fuel).toEqual("Petrol")
    expect(result.weight).toEqual(2015)
    expect(result.error).toEqual(null)
  }

  @Test
  fun `should return error result when API call fails`() {
    val plateNumber = "INVALID"
    every { veegoClient.getTaxInfo(plateNumber, VeegoTaxRequest(plateNumber)) } throws RuntimeException("Connection refused")

    val result = veegoService.getTaxInfo(plateNumber)

    expect(result.annualTax).toEqual(null)
    expect(result.registrationTax).toEqual(null)
    expect(result.make).toEqual(null)
    expect(result.model).toEqual(null)
    expect(result.error).toEqual("Connection refused")
  }
}
