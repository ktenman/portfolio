package ee.tenman.portfolio.auto24

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.exception.CaptchaException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Auto24ServiceTest {
  private val auto24WebClient = mockk<Auto24WebClient>()
  private lateinit var auto24Service: Auto24Service

  @BeforeEach
  fun setUp() {
    auto24Service = Auto24Service(auto24WebClient)
  }

  @Test
  fun `should return price when API call succeeds`() =
    runTest {
      val regNr = "876BCH"
      val response =
        Auto24PriceResponse(
          registrationNumber = regNr,
          marketPrice = "3400 kuni 8300 EUR",
          error = null,
          attempts = 1,
          durationSeconds = 1.5,
        )
      coEvery { auto24WebClient.getMarketPrice(regNr) } returns response

      val result = auto24Service.findCarPrice(regNr)

      expect(result.price).toEqual("3400 kuni 8300 EUR")
      expect(result.error).toEqual(null)
      expect(result.durationSeconds).toEqual(1.5)
    }

  @Test
  fun `should return error when vehicle not found`() =
    runTest {
      val regNr = "INVALID"
      val response =
        Auto24PriceResponse(
          registrationNumber = regNr,
          marketPrice = null,
          error = "Vehicle not found",
          attempts = 1,
          durationSeconds = 0.5,
        )
      coEvery { auto24WebClient.getMarketPrice(regNr) } returns response

      val result = auto24Service.findCarPrice(regNr)

      expect(result.price).toEqual(null)
      expect(result.error).toEqual("Vehicle not found")
      expect(result.durationSeconds).toEqual(0.5)
    }

  @Test
  fun `should return error when price not available in error field`() =
    runTest {
      val regNr = "123ABC"
      val response =
        Auto24PriceResponse(
          registrationNumber = regNr,
          marketPrice = null,
          error = "Price not available",
          attempts = 2,
          durationSeconds = 0.3,
        )
      coEvery { auto24WebClient.getMarketPrice(regNr) } returns response

      val result = auto24Service.findCarPrice(regNr)

      expect(result.price).toEqual(null)
      expect(result.error).toEqual("Price not available")
      expect(result.durationSeconds).toEqual(0.3)
    }

  @Test
  fun `should return price not available when marketPrice is null`() =
    runTest {
      val regNr = "456DEF"
      val response =
        Auto24PriceResponse(
          registrationNumber = regNr,
          marketPrice = null,
          error = null,
          attempts = 1,
          durationSeconds = 0.8,
        )
      coEvery { auto24WebClient.getMarketPrice(regNr) } returns response

      val result = auto24Service.findCarPrice(regNr)

      expect(result.price).toEqual(null)
      expect(result.error).toEqual("Price not available")
      expect(result.durationSeconds).toEqual(0.8)
    }

  @Test
  fun `should throw CaptchaException when unknown error occurs`() {
    val regNr = "789GHI"
    val response =
      Auto24PriceResponse(
        registrationNumber = regNr,
        marketPrice = null,
        error = "Captcha verification failed",
        attempts = 3,
        durationSeconds = 2.0,
      )
    coEvery { auto24WebClient.getMarketPrice(regNr) } returns response

    expect { runBlocking { auto24Service.findCarPrice(regNr) } }
      .toThrow<CaptchaException>()
      .messageToContain("Captcha verification failed")
  }
}
