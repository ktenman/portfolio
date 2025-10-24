package ee.tenman.portfolio.domain

import java.io.Serializable
import java.time.LocalDate

data class EtfPositionId(
  var etfInstrument: Long = 0,
  var holding: Long = 0,
  var snapshotDate: LocalDate = LocalDate.now(),
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
