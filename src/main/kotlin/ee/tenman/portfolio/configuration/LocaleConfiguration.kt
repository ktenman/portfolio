package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.CookieLocaleResolver
import java.util.*

@Configuration
class LocaleConfiguration {

  @Configuration
  class LocaleConfiguration {

    @Bean
    fun localeResolver(): LocaleResolver {
      return CookieLocaleResolver().apply {
        setDefaultLocale(Locale.forLanguageTag("et-EE"))
        setDefaultTimeZone(TimeZone.getTimeZone("Europe/Tallinn"))
      }
    }

    @Bean
    fun messageSource(): ResourceBundleMessageSource {
      return ResourceBundleMessageSource().apply {
        setBasenames("messages")
        setDefaultEncoding("UTF-8")
      }
    }
  }
}
