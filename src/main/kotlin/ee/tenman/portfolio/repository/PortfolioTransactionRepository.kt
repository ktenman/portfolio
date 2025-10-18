package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PortfolioTransactionRepository : JpaRepository<PortfolioTransaction, Long> {
  @Query("SELECT pt FROM PortfolioTransaction pt JOIN FETCH pt.instrument ORDER BY pt.transactionDate DESC, pt.id DESC")
  fun findAllWithInstruments(): List<PortfolioTransaction>

  fun findAllByInstrumentIdAndPlatformOrderByTransactionDate(
    instrumentId: Long,
    platform: Platform,
  ): List<PortfolioTransaction>

  @Query(
    """
    SELECT pt FROM PortfolioTransaction pt
    JOIN FETCH pt.instrument
    WHERE pt.instrument.id = :instrumentId
    ORDER BY pt.transactionDate DESC, pt.id DESC
    """,
  )
  fun findAllByInstrumentId(instrumentId: Long): List<PortfolioTransaction>

  @Query(
    """
    SELECT pt FROM PortfolioTransaction pt
    JOIN FETCH pt.instrument
    WHERE pt.platform IN :platforms
    ORDER BY pt.transactionDate DESC, pt.id DESC
    """,
  )
  fun findAllByPlatformsWithInstruments(platforms: List<Platform>): List<PortfolioTransaction>
}
