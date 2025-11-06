package ee.tenman.portfolio

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude = [RedisRepositoriesAutoConfiguration::class])
@EnableFeignClients
@EnableScheduling
class PortfolioApplication

fun main(args: Array<String>) {
  runApplication<PortfolioApplication>(*args)
}
