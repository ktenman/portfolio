package ee.tenman.portfolio.service.diversification

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_CONFIG_CACHE
import ee.tenman.portfolio.domain.ActionDisplayMode
import ee.tenman.portfolio.domain.DiversificationAllocationData
import ee.tenman.portfolio.domain.DiversificationConfig
import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.InputMode
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.DiversificationConfigAllocationDto
import ee.tenman.portfolio.dto.DiversificationConfigDto
import ee.tenman.portfolio.repository.DiversificationConfigRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiversificationConfigService(
  private val repository: DiversificationConfigRepository,
  private val instrumentRepository: InstrumentRepository,
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [DIVERSIFICATION_CONFIG_CACHE], key = "'config'")
  fun getConfig(): DiversificationConfigDto? = repository.findConfig()?.toDto()

  @Transactional
  @CacheEvict(value = [DIVERSIFICATION_CONFIG_CACHE], key = "'config'")
  fun saveConfig(dto: DiversificationConfigDto): DiversificationConfigDto {
    validate(dto)
    val configData = dto.toConfigData()
    val config =
      repository.findConfig()?.apply { this.configData = configData }
        ?: DiversificationConfig(configData = configData)
    return repository.save(config).toDto()
  }

  private fun validate(dto: DiversificationConfigDto) {
    val funds = instrumentRepository.findByProviderName(ProviderName.SYNTHETIC).map { it.id }.toSet()
    val rejected = dto.allocations.map { it.instrumentId }.filter { it in funds }
    require(rejected.isEmpty()) { "Configuration allocates to synthetic funds $rejected, which their constituents have replaced" }
  }

  private fun DiversificationConfig.toDto() =
    DiversificationConfigDto(
      allocations = configData.allocations.map { it.toDto() },
      inputMode = configData.inputMode.name.lowercase(),
      selectedPlatforms = configData.selectedPlatforms,
      optimizeEnabled = configData.optimizeEnabled,
      totalInvestment = configData.totalInvestment,
      actionDisplayMode = configData.actionDisplayMode.name.lowercase(),
      buyOnlyEnabled = configData.buyOnlyEnabled,
    )

  private fun DiversificationAllocationData.toDto() =
    DiversificationConfigAllocationDto(
      instrumentId = instrumentId,
      value = value,
    )

  private fun DiversificationConfigDto.toConfigData() =
    DiversificationConfigData(
      allocations = allocations.map { it.toAllocationData() },
      inputMode = InputMode.fromString(inputMode),
      selectedPlatforms = selectedPlatforms,
      optimizeEnabled = optimizeEnabled,
      totalInvestment = totalInvestment,
      actionDisplayMode = ActionDisplayMode.fromString(actionDisplayMode),
      buyOnlyEnabled = buyOnlyEnabled,
    )

  private fun DiversificationConfigAllocationDto.toAllocationData() =
    DiversificationAllocationData(
      instrumentId = instrumentId,
      value = value,
    )
}
