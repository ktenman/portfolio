package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.FundAllocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FundAllocationRepository : JpaRepository<FundAllocation, Long> {
  fun findByFundIsin(fundIsin: String): List<FundAllocation>

  fun findFirstByFundIsinOrderByAsOfDateDescIdDesc(fundIsin: String): FundAllocation?

  fun findBySourceUrl(sourceUrl: String): List<FundAllocation>
}
