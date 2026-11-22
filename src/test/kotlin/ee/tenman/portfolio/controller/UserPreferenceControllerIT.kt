package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@IntegrationTest
class UserPreferenceControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Test
  fun `should answer AUTO for a user without a saved preference`() {
    find("uus.kasutaja@example.ee").andExpect(status().isOk).andExpect(jsonPath("$.theme").value("AUTO"))
  }

  @Test
  fun `should return the theme a user saved`() {
    choose("tiit.eelistus@näide.ee", "DARK")
    find("tiit.eelistus@näide.ee").andExpect(jsonPath("$.theme").value("DARK"))
  }

  @Test
  fun `should keep the last of two saved themes`() {
    choose("mari.eelistus@example.ee", "DARK")
    choose("mari.eelistus@example.ee", "LIGHT")
    find("mari.eelistus@example.ee").andExpect(jsonPath("$.theme").value("LIGHT"))
  }

  @Test
  fun `should keep one users theme apart from anothers`() {
    choose("jaan.eelistus@example.ee", "DARK")
    choose("liis.eelistus@example.ee", "LIGHT")
    find("jaan.eelistus@example.ee").andExpect(jsonPath("$.theme").value("DARK"))
  }

  @Test
  fun `should refuse an unknown theme`() {
    save("peeter.eelistus@example.ee", """{"theme":"SEPIA"}""").andExpect(status().is4xxClientError)
    find("peeter.eelistus@example.ee").andExpect(jsonPath("$.theme").value("AUTO"))
  }

  @Test
  fun `should refuse a request without a user`() {
    mockMvc.perform(get("/api/preferences")).andExpect(status().is4xxClientError)
  }

  private fun find(user: String) = mockMvc.perform(get("/api/preferences").header(USER, user))

  private fun save(
    user: String,
    body: String,
  ) = mockMvc.perform(put("/api/preferences").header(USER, user).contentType(APPLICATION_JSON).content(body))

  private fun choose(
    user: String,
    theme: String,
  ) {
    save(user, """{"theme":"$theme"}""").andExpect(status().isNoContent)
  }
}

private const val USER = "X-User-Id"
