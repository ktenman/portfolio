package ee.tenman.portfolio.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
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
      ).addSecurityItem(SecurityRequirement().addList("bearerAuth"))
      .components(
        Components()
          .addSecuritySchemes(
            "bearerAuth",
            SecurityScheme()
              .name("bearerAuth")
              .type(SecurityScheme.Type.HTTP)
              .scheme("bearer")
              .bearerFormat("JWT"),
          ),
      )
}
