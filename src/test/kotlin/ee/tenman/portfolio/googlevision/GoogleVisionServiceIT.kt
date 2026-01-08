package ee.tenman.portfolio.googlevision

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*
import javax.annotation.Resource

@IntegrationTest
class GoogleVisionServiceIT {
  @Resource
  private lateinit var googleVisionService: GoogleVisionService

  @Test
  @Disabled
  fun `should extract plate number when given base64 encoded image`() {
    val base64EncodedImage = FileToBase64.encodeToBase64("2024-12-08 16.35.53.jpg")
    val plateNumber = googleVisionService.getPlateNumber(base64EncodedImage, UUID.randomUUID())

    expect(plateNumber["plateNumber"]).toEqual("116NVM")
  }
}
