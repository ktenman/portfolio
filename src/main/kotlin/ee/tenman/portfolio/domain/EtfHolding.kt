package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
  name = "etf_holding",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["name", "ticker"]),
  ],
)
class EtfHolding(
  @Column(length = 50)
  var ticker: String? = null,
  @Column(nullable = false, length = 255)
  var name: String,
  @Column(length = 150)
  var sector: String? = null,
  @Enumerated(EnumType.STRING)
  @Column(name = "classified_by_model", length = 100)
  var classifiedByModel: AiModel? = null,
  @Enumerated(EnumType.STRING)
  @Column(name = "sector_source", length = 20)
  var sectorSource: SectorSource? = null,
  @Column(name = "country_code", length = 2)
  var countryCode: String? = null,
  @Column(name = "country_name", length = 100)
  var countryName: String? = null,
  @Enumerated(EnumType.STRING)
  @Column(name = "country_classified_by_model", length = 100)
  var countryClassifiedByModel: AiModel? = null,
) : BaseEntity()
