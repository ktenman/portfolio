package ee.tenman.portfolio.service.etf

import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class HoldingWriteLock {
  private val lock = ReentrantLock()

  fun <T> exclusively(block: () -> T): T = lock.withLock(block)
}
