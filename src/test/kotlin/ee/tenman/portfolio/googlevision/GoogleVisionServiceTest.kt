package ee.tenman.portfolio.googlevision

import ee.tenman.portfolio.configuration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*
import javax.annotation.Resource

@IntegrationTest
class GoogleVisionServiceTest {

  @Resource
  private lateinit var googleVisionService: GoogleVisionService

  @Test
  @Disabled
  fun getPlateNumber() {
    val base64EncodedImage = FileToBase64.encodeToBase64("2024-12-08 16.35.53.jpg")
    val plateNumber = googleVisionService.getPlateNumber(base64EncodedImage, UUID.randomUUID())

    assertThat(plateNumber["plateNumber"]).isEqualTo("116NVM")
  }

}
