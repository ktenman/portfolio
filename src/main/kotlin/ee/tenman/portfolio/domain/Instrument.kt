package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "instrument")
class Instrument(
  @Column(unique = true, nullable = false)
  var symbol: String,
  @Column(nullable = true)
  var name: String,
  @Column(name = "instrument_category", nullable = true)
  var category: String,
  @Column(name = "base_currency", nullable = false)
  var baseCurrency: String,
  @Column(name = "current_price", nullable = true)
  var currentPrice: BigDecimal? = null,
  @Enumerated(EnumType.STRING)
  @Column(name = "provider_name", nullable = true)
  var providerName: ProviderName = ProviderName.FT,
  @Column(name = "provider_external_id", nullable = true)
  var providerExternalId: String? = null,
  @Column(name = "ter", nullable = true)
  var ter: BigDecimal? = null,
  @Column(name = "xirr_annual_return", nullable = true)
  var xirrAnnualReturn: BigDecimal? = null,
) : BaseEntity() {
  fun isCash(): Boolean = category == InstrumentCategory.CASH.name
}
