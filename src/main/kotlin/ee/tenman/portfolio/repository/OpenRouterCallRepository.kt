package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.OpenRouterCall
import ee.tenman.portfolio.domain.OpenRouterMonthlySpend
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface OpenRouterCallRepository : JpaRepository<OpenRouterCall, Long> {
  @Query(
    """
    SELECT new ee.tenman.portfolio.domain.OpenRouterMonthlySpend(FORMAT(c.day AS 'yyyy-MM'), c.purpose, c.model, SUM(c.cost))
    FROM OpenRouterCall c
    WHERE c.day >= :since
    GROUP BY FORMAT(c.day AS 'yyyy-MM'), c.purpose, c.model
    """,
  )
  fun findMonthlySpendSince(since: LocalDate): List<OpenRouterMonthlySpend>
}
