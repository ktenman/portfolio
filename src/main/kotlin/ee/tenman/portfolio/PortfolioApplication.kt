package ee.tenman.portfolio

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableResilientMethods
@EnableScheduling
class PortfolioApplication

fun main(args: Array<String>) {
  runApplication<PortfolioApplication>(*args)
}
