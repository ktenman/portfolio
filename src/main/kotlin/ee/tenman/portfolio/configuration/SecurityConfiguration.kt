package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .authorizeHttpRequests { authorize ->
        authorize.anyRequest().permitAll()
      }
      .csrf { it.disable() }
      .httpBasic { it.disable() }
      .formLogin { it.disable() }

    return http.build()
  }
}
