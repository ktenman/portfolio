package ee.tenman.portfolio.configuration

import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class XirrCalculationConfig {
  @Bean
  fun calculationDispatcher() =
    Dispatchers.Default.limitedParallelism(
      Runtime.getRuntime().availableProcessors(),
    )
}
