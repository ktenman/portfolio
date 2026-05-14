package ee.tenman.portfolio.telegram

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.objects.PhotoSize

class CarTelegramBotTest {
  @Test
  fun `should pick smallest photo whose max dimension reaches plate detection threshold`() {
    val photos =
      listOf(
        photoSize("thumb-90", 90, 90),
        photoSize("thumb-320", 320, 240),
        photoSize("thumb-800", 800, 600),
        photoSize("thumb-1280", 1280, 960),
        photoSize("original-4032", 4032, 3024),
      )

    val pick = CarTelegramBot.pickPhotoForPlateDetection(photos)

    expect(pick?.fileId).toEqual("thumb-1280")
  }

  @Test
  fun `should fall back to largest photo when none meet the threshold`() {
    val photos =
      listOf(
        photoSize("thumb-90", 90, 90),
        photoSize("thumb-320", 320, 240),
        photoSize("thumb-800", 800, 600),
      )

    val pick = CarTelegramBot.pickPhotoForPlateDetection(photos)

    expect(pick?.fileId).toEqual("thumb-800")
  }

  @Test
  fun `should use height when it exceeds width for portrait photos`() {
    val photos =
      listOf(
        photoSize("portrait-800", 600, 800),
        photoSize("portrait-1280", 960, 1280),
      )

    val pick = CarTelegramBot.pickPhotoForPlateDetection(photos)

    expect(pick?.fileId).toEqual("portrait-1280")
  }

  @Test
  fun `should return null when photo list is empty`() {
    val pick = CarTelegramBot.pickPhotoForPlateDetection(emptyList())

    expect(pick).toEqual(null)
  }

  private fun photoSize(
    id: String,
    width: Int,
    height: Int,
  ): PhotoSize =
    PhotoSize().apply {
      this.fileId = id
      this.fileUniqueId = id
      this.width = width
      this.height = height
    }
}
