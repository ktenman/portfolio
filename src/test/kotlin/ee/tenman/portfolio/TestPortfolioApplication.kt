package ee.tenman.portfolio

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
	fromApplication<PortfolioApplication>().with(TestcontainersConfiguration::class).run(*args)
}
