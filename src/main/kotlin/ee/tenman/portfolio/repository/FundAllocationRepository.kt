package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.FundAllocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface FundAllocationRepository : JpaRepository<FundAllocation, Long> {
  @Query("SELECT DISTINCT a.sourceUrl FROM FundAllocation a WHERE a.fundIsin = :fundIsin")
  fun findSourceUrls(fundIsin: String): Set<String>

  fun findFirstByFundIsinOrderByAsOfDateDescIdDesc(fundIsin: String): FundAllocation?

  fun findBySourceUrl(sourceUrl: String): List<FundAllocation>
}
