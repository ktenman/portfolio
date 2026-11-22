package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.UserPreferenceDto
import ee.tenman.portfolio.service.common.UserPreferenceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/preferences")
class UserPreferenceController(
  private val userPreferenceService: UserPreferenceService,
) {
  @GetMapping
  fun getPreference(
    @RequestHeader("X-User-Id") user: String,
  ): UserPreferenceDto = userPreferenceService.find(user)

  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun putPreference(
    @RequestHeader("X-User-Id") user: String,
    @RequestBody preference: UserPreferenceDto,
  ) = userPreferenceService.save(user, preference)
}
