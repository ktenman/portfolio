package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.inAnyOrder
import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.only
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.values
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.skyplus.SkyplusScheduleClient
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test

@IntegrationTest
class SkyplusScheduleJobIT : RadioScheduleTestBase() {
  @Resource
  private lateinit var skyplusScheduleClient: SkyplusScheduleClient

  @Test
  fun `should store the template rows as Sky Plus programmes`() {
    schedule(row(4, "Õhtune Äratus", 1, 2, 3, 4, 5), row(20, "Õhtu Plus", 1, 2, 3, 4))
    job().execute()
    expect(radioScheduleService.programmes().filter { it.station == Station.SKYPLUS }.map { it.name })
      .toContain.inAnyOrder.only
      .values("Õhtune Äratus", "Õhtu Plus")
  }

  @Test
  fun `should succeed on a day when Sky Plus airs no shows`() {
    schedule()
    expect { job().execute() }.notToThrow()
  }

  @Test
  fun `should keep the airings of a show that does not air today`() {
    schedule(row(4, "Õhtune Äratus", day.dayOfWeek.value))
    job().execute()
    schedule(row(20, "Õhtu Plus", day.dayOfWeek.minus(1).value))
    job().execute()
    expect(radioAiringRepository.findAll().map { it.title }).toContain("Õhtune Äratus")
  }

  private fun job() = SkyplusScheduleJob(skyplusScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock, radioProperties)

  private fun row(
    id: Long,
    name: String,
    vararg days: Int,
  ) = """{"id":$id,"name":"$name","days":[${days.joinToString(",")}],"start_time":"15:00","end_time":"18:00"}"""

  private fun schedule(vararg rows: String) {
    val body = """{"data":[${rows.joinToString(",")}]}"""
    wireMockServer.stubFor(get(urlPathEqualTo("/api/radio-stations/1/schedule")).willReturn(okJson(body)))
  }
}
