package ee.tenman.portfolio.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
  @Bean
  fun customOpenAPI(): OpenAPI =
    OpenAPI()
      .info(
        Info()
          .title("Portfolio Management API")
          .version("1.0")
          .description("API for managing investment portfolios with automated price tracking"),
      )
}
