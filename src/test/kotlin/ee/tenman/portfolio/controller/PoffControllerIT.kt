package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.PoffCatalogue
import ee.tenman.portfolio.domain.PoffImdb
import ee.tenman.portfolio.domain.PoffSnapshot
import ee.tenman.portfolio.repository.PoffImdbRepository
import ee.tenman.portfolio.repository.PoffSnapshotRepository
import ee.tenman.portfolio.service.poff.poffFilm
import jakarta.annotation.Resource
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

@IntegrationTest
class PoffControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var poffSnapshotRepository: PoffSnapshotRepository

  @Resource
  private lateinit var poffImdbRepository: PoffImdbRepository

  @Resource
  private lateinit var jdbcTemplate: JdbcTemplate

  private val synced = Instant.parse("2026-11-10T15:32:00Z")

  private fun store() {
    poffSnapshotRepository.save(PoffSnapshot(PoffCatalogue(listOf(poffFilm(8810, title = "Armastatu"), poffFilm(9107))), synced))
    poffImdbRepository.save(PoffImdb(8810, "tt27714581", 9, BigDecimal("7.2"), 4184, synced))
    poffImdbRepository.save(PoffImdb(9107, null, null, null, null, synced))
  }

  @Test
  fun `answers not found before the first import`() {
    mockMvc.perform(mvcGet("/api/poff/catalogue")).andExpect(status().isNotFound)
  }

  @Test
  fun `answers not found when the stored catalogue cannot be read`() {
    jdbcTemplate.update("INSERT INTO poff_snapshot (catalogue, synced_at) VALUES (?, now())", """{"films":[{"id":1}]}""")
    mockMvc.perform(mvcGet("/api/poff/catalogue")).andExpect(status().isNotFound)
  }

  @Test
  fun `returns the catalogue with each matched film's rating`() {
    store()
    mockMvc
      .perform(mvcGet("/api/poff/catalogue"))
      .andExpect(jsonPath("$.films[*].imdb.votes", contains(4184)))
  }

  @Test
  fun `answers not modified when the catalogue is unchanged since the last request`() {
    store()
    val etag =
      mockMvc
      .perform(mvcGet("/api/poff/catalogue"))
      .andReturn()
      .response
      .getHeader("ETag")
      .orEmpty()
    mockMvc.perform(mvcGet("/api/poff/catalogue").header("If-None-Match", etag)).andExpect(status().isNotModified)
  }

  @Test
  fun `returns the time of the last sync`() {
    store()
    mockMvc.perform(mvcGet("/api/poff/catalogue")).andExpect(jsonPath("$.syncedAt").value("2026-11-10T15:32:00Z"))
  }
}
