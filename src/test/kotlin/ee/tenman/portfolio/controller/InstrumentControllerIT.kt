package ee.tenman.portfolio.controller

import ee.tenman.portfolio.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@IntegrationTest
class InstrumentControllerIT {

  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Test
  fun `should save instrument when POST request is made to instruments endpoint`() {
    mockMvc.perform(
      post("/api/instruments").contentType(MediaType.APPLICATION_JSON).content(
        """
        {
          "symbol": "QDVE",
          "name": "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          "category": "ETF",
          "baseCurrency": "EUR"
        }
      """.trimIndent()
      )
    ).andExpect(status().isOk).andExpect(jsonPath("$.symbol").value("QDVE"))
      .andExpect(jsonPath("$.name").value("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$.category").value("ETF")).andExpect(jsonPath("$.baseCurrency").value("EUR"))

    assertThat(instrumentRepository.findAll()).singleElement().satisfies({ instrument ->
      assertThat(instrument.symbol).isEqualTo("QDVE")
      assertThat(instrument.name).isEqualTo("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)")
      assertThat(instrument.category).isEqualTo("ETF")
      assertThat(instrument.baseCurrency).isEqualTo("EUR")
    })
  }

  @Test
  fun `should return all instruments when GET request is made to instruments endpoint`() {
    instrumentRepository.saveAll(
      listOf(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR"
        ),
        Instrument(
          symbol = "IUSA",
          name = "iShares Core S&P 500 UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "USD"
        )
      )
    )

    mockMvc.perform(get("/api/instruments"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$[0].symbol").value("QDVE"))
      .andExpect(jsonPath("$[0].name").value("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$[0].category").value("ETF"))
      .andExpect(jsonPath("$[0].baseCurrency").value("EUR"))
      .andExpect(jsonPath("$[1].symbol").value("IUSA"))
      .andExpect(jsonPath("$[1].name").value("iShares Core S&P 500 UCITS ETF USD (Acc)"))
      .andExpect(jsonPath("$[1].category").value("ETF"))
      .andExpect(jsonPath("$[1].baseCurrency").value("USD"))
  }


  @Test
  fun `should update instrument when PUT request is made to instruments endpoint with valid id`() {
    val savedInstrument = instrumentRepository.save(
      Instrument(
        symbol = "QDVE",
        name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        category = "ETF",
        baseCurrency = "EUR"
      )
    )

    mockMvc.perform(
      put("/api/instruments/{id}", savedInstrument.id).contentType(MediaType.APPLICATION_JSON).content(
        """
        {
          "symbol": "QDVE",
          "name": "Updated Instrument Name",
          "category": "ETF",
          "baseCurrency": "USD"
        }
      """.trimIndent()
      )
    ).andExpect(status().isOk).andExpect(jsonPath("$.symbol").value("QDVE"))
      .andExpect(jsonPath("$.name").value("Updated Instrument Name")).andExpect(jsonPath("$.category").value("ETF"))
      .andExpect(jsonPath("$.baseCurrency").value("USD"))

    val updatedInstrument = instrumentRepository.findById(savedInstrument.id).get()
    assertThat(updatedInstrument.name).isEqualTo("Updated Instrument Name")
    assertThat(updatedInstrument.baseCurrency).isEqualTo("USD")
  }

  @Test
  fun `should delete instrument when DELETE request is made to instruments endpoint with valid id`() {
    val savedInstrument = instrumentRepository.save(
      Instrument(
        symbol = "QDVE",
        name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        category = "ETF",
        baseCurrency = "EUR"
      )
    )

    mockMvc.perform(delete("/api/instruments/{id}", savedInstrument.id)).andExpect(status().isNoContent)

    assertThat(instrumentRepository.findById(savedInstrument.id)).isEmpty
  }
}
