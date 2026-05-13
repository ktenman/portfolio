package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.io.File
import java.util.Base64

@Disabled("Hits the real Google Vision API")
class GoogleVisionServiceLiveTest {
  @Test
  fun `extracts plate text from synthetic plate image`() {
    val apiKey = System.getenv("GOOGLE_VISION_API_KEY") ?: error("set GOOGLE_VISION_API_KEY")
    val service = GoogleVisionService(apiKey, RestClient.create())
    val image = Base64.getEncoder().encodeToString(File("/tmp/plate-test.png").readBytes())
    val text = service.extractText(image)
    println("Detected text: $text")
    expect(text).notToEqualNull().toContain("678", "WKS")
  }
}
