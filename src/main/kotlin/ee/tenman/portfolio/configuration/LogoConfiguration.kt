package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(LogoReplacementProperties::class, BatchLogoValidationProperties::class)
class LogoConfiguration
