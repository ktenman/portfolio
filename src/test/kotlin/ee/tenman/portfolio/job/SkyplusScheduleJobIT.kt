package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.inAnyOrder
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
    wireMockServer.stubFor(
      get(urlPathEqualTo("/api/radio-stations/1/schedule")).willReturn(
        okJson(
          """{"data":[{"id":4,"name":"Õhtune Äratus","description":null,"days":[1,2,3,4,5],""" +
            """"start_time":"15:00","end_time":"18:00","show":null},""" +
            """{"id":20,"name":"Õhtu Plus","description":null,"days":[1,2,3,4],""" +
            """"start_time":"18:00","end_time":"22:00","show":null}]}""",
        ),
      ),
    )
    SkyplusScheduleJob(skyplusScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock, radioProperties).execute()
    expect(radioScheduleService.programmes().filter { it.station == Station.SKYPLUS }.map { it.name })
      .toContain.inAnyOrder.only
      .values("Õhtune Äratus", "Õhtu Plus")
  }
}
