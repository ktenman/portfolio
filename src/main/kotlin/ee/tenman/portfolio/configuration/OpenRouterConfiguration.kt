package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.openrouter.OpenRouterProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OpenRouterProperties::class, IndustryClassificationProperties::class)
class OpenRouterConfiguration
