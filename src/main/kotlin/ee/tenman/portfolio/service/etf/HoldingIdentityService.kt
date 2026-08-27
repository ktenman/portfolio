package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.HoldingNameSimilarity
import org.springframework.stereotype.Service

@Service
class HoldingIdentityService(
  private val cacheService: HoldingIdentityCacheService,
  private val properties: IndustryClassificationProperties,
) {
  fun isSameCompany(
    existingName: String,
    candidateName: String,
    ticker: String?,
  ): Boolean? {
    if (!properties.enabled) return null
    if (existingName.isBlank() || candidateName.isBlank()) return null
    if (existingName.equals(candidateName, ignoreCase = true)) return true
    if (!HoldingNameSimilarity.mayBeSameCompany(existingName, candidateName)) return false
    return cacheService.resolve(existingName, candidateName, ticker)
  }
}
