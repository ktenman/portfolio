package ee.tenman.portfolio.service

import ee.tenman.portfolio.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class InstrumentServiceTest {
  @Autowired
  private lateinit var instrumentRepository: InstrumentRepository

  @Test
  fun `should save and retrieve instrument when repository operations are performed`() {
    val instrument = Instrument(
      "QDVE",
      "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
      "ETF"
    )

    instrumentRepository.save(instrument)

    assertThat(instrumentRepository.findAll()).isNotEmpty.singleElement().satisfies({
      assertThat(it.symbol).isEqualTo("QDVE")
      assertThat(it.name).isEqualTo("iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)")
      assertThat(it.category).isEqualTo("ETF")
    })
  }
}
