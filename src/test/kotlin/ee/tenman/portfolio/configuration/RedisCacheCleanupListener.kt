package ee.tenman.portfolio.configuration

import io.mockk.clearMocks
import io.mockk.isMockKMock
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener
import java.time.Clock

class RedisCacheCleanupListener : AbstractTestExecutionListener() {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun beforeTestMethod(testContext: TestContext) {
    val cacheManager = testContext.applicationContext.getBean(CacheManager::class.java)
    cacheManager.cacheNames.forEach { cacheName ->
      cacheManager.getCache(cacheName)?.clear()
    }

    try {
      val redisTemplate = testContext.applicationContext.getBean(RedisTemplate::class.java) as RedisTemplate<String, Any>
      redisTemplate.connectionFactory?.connection?.use { connection ->
        connection.serverCommands().flushAll()
      }
    } catch (e: Exception) {
      log.trace("Redis flush failed, may not be available in this test context", e)
    }

    try {
      val clock = testContext.applicationContext.getBean(Clock::class.java)
      if (isMockKMock(clock)) {
        clearMocks(clock)
      }
    } catch (e: Exception) {
      log.trace("Clock mock cleanup skipped, may not be mocked in this test", e)
    }
  }
}
