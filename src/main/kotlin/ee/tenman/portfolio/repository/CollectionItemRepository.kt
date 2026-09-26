package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.CollectionItem
import ee.tenman.portfolio.domain.CollectionKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CollectionItemRepository : JpaRepository<CollectionItem, Long> {
  fun findAllByKey(key: CollectionKey): List<CollectionItem>

  fun findByKeyAndSymbolAndActiveTrue(
    key: CollectionKey,
    symbol: String,
  ): CollectionItem?
}
