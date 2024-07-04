package ee.tenman.portfolio.alphavantage

import com.google.common.util.concurrent.RateLimiter

class SimplifiedRateLimiter {
  companion object {
    private const val RATE = 29.0 / 60.0 // 29 requests per minute
  }

  private val rateLimiter: RateLimiter = RateLimiter.create(RATE)

  fun execute(task: Runnable) {
    rateLimiter.acquire() // This will block if necessary to ensure the rate
    task.run()
  }
}
