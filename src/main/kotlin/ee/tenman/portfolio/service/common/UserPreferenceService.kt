package ee.tenman.portfolio.service.common

import ee.tenman.portfolio.domain.Theme
import ee.tenman.portfolio.dto.UserPreferenceDto
import ee.tenman.portfolio.repository.UserPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserPreferenceService(
  private val userPreferenceRepository: UserPreferenceRepository,
) {
  fun find(user: String): UserPreferenceDto = UserPreferenceDto(userPreferenceRepository.findByUserId(user)?.theme ?: Theme.AUTO)

  @Transactional
  fun save(
    user: String,
    preference: UserPreferenceDto,
  ) = userPreferenceRepository.upsertTheme(user, preference.theme.name)
}
