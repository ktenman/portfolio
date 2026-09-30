package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PendingHoldingImport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface PendingHoldingImportRepository : JpaRepository<PendingHoldingImport, Long> {
  @Query(
    """
    SELECT p FROM PendingHoldingImport p
    WHERE p.id IN (SELECT MAX(q.id) FROM PendingHoldingImport q GROUP BY q.etfSymbol)
    ORDER BY p.id
  """,
  )
  fun findLatestPerSymbol(): List<PendingHoldingImport>

  @Modifying
  @Transactional
  @Query("DELETE FROM PendingHoldingImport p WHERE p.etfSymbol = :etfSymbol AND p.id <= :id")
  fun deleteByEtfSymbolAndIdLessThanEqual(
    @Param("etfSymbol") etfSymbol: String,
    @Param("id") id: Long,
  ): Int
}
