package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PoffImdb
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PoffImdbRepository : JpaRepository<PoffImdb, Long> {
  fun findByImdbIdIsNotNull(): List<PoffImdb>
}
