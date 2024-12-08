package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@IntegrationTest
class Auto24Test {

  @Resource
  private lateinit var auto24Service: Auto24Service

  @Test
  @Disabled
  fun findCarPrice() {
    val findCarPrice = auto24Service.findCarPrice("463BKH")

    assertThat(findCarPrice).isEqualTo("6200 € kuni 10000 €")
  }

}
