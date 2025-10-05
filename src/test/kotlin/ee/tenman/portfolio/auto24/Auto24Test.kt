package ee.tenman.portfolio.auto24

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@IntegrationTest
class Auto24Test {
  @Resource
  private lateinit var auto24Service: Auto24Service

  @Test
  @Disabled
  fun `should return car price range when finding car by license plate`() {
    val findCarPrice = auto24Service.findCarPrice("463BKH")

    expect(findCarPrice).toEqual("6200 € kuni 10000 €")
  }
}
