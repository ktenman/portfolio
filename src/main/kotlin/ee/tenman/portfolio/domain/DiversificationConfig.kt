package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "diversification_config")
class DiversificationConfig(
  @Column(name = "config_data", nullable = false, columnDefinition = "text")
  var configData: DiversificationConfigData,
) : BaseEntity()
