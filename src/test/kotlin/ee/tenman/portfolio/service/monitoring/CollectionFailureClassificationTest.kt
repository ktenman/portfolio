package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import feign.FeignException
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.UnexpectedRollbackException
import java.net.SocketTimeoutException
import java.sql.SQLException

class CollectionFailureClassificationTest {
  @Test
  fun `should classify missing items as invalid responses`() {
    expect(classify(null)).toEqual("invalid_response" to "none")
  }

  @Test
  fun `should classify malformed values as invalid responses`() {
    expect(classify(NumberFormatException("bad value"))).toEqual("invalid_response" to "none")
  }

  @Test
  fun `should classify database failures as persistence errors`() {
    expect(classify(SQLException("database unavailable"))).toEqual("persistence" to "none")
  }

  @Test
  fun `should classify a failed commit as a persistence error`() {
    expect(classify(UnexpectedRollbackException("commit rolled back"))).toEqual("persistence" to "none")
  }

  @Test
  fun `should classify untranslated database errors as persistence failures`() {
    expect(classify(PersistenceException("database write failed"))).toEqual("persistence" to "none")
  }

  @Test
  fun `should retain persistence category for wrapped SQL failures`() {
    val error = DataAccessResourceFailureException("write failed", SQLException("database unavailable"))
    expect(classify(error)).toEqual("persistence" to "none")
  }

  @Test
  fun `should classify timeouts without using exception messages as labels`() {
    expect(classify(SocketTimeoutException("sensitive endpoint"))).toEqual("timeout" to "none")
  }

  @Test
  fun `should classify rate limits with bounded status`() {
    val error = mockk<FeignException>()
    every { error.status() } returns 429
    every { error.cause } returns null
    expect(classify(error)).toEqual("rate_limit" to "429")
  }

  @Test
  fun `should classify timeout inside an unreported HTTP status`() {
    val error = mockk<FeignException>()
    every { error.status() } returns -1
    every { error.cause } returns SocketTimeoutException("private endpoint")
    expect(classify(error)).toEqual("timeout" to "none")
  }

  @Test
  fun `should classify malformed response inside a successful HTTP status`() {
    val error = mockk<FeignException>()
    every { error.status() } returns 200
    every { error.cause } returns NumberFormatException("bad response")
    expect(classify(error)).toEqual("invalid_response" to "none")
  }
}
