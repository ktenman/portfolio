package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfiguration {
  @Bean
  fun clock(): Clock = Clock.system(ZoneId.of("Europe/Tallinn"))
}
