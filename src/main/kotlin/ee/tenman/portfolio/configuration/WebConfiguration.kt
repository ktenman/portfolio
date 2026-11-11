package ee.tenman.portfolio.configuration

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.ShallowEtagHeaderFilter

@Configuration
class WebConfiguration {
  @Bean
  fun etagFilter(): FilterRegistrationBean<ShallowEtagHeaderFilter> =
    FilterRegistrationBean(ShallowEtagHeaderFilter()).apply { addUrlPatterns("/api/poff/*") }
}
