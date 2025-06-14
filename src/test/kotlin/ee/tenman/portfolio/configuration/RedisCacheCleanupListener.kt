package ee.tenman.portfolio.configuration

import org.mockito.Mockito
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener
import java.time.Clock

/**
 * Test execution listener that clears Redis cache and resets mocks before each test method
 * to ensure test isolation
 */
class RedisCacheCleanupListener : AbstractTestExecutionListener() {
  override fun beforeTestMethod(testContext: TestContext) {
    // Clear all Spring caches
    val cacheManager = testContext.applicationContext.getBean(CacheManager::class.java)
    cacheManager.cacheNames.forEach { cacheName ->
      cacheManager.getCache(cacheName)?.clear()
    }

    // Clear Redis directly if available
    try {
      val redisTemplate = testContext.applicationContext.getBean(RedisTemplate::class.java) as RedisTemplate<String, Any>
      redisTemplate.connectionFactory?.connection?.use { connection ->
        connection.serverCommands().flushAll()
      }
    } catch (e: Exception) {
      // Redis template might not be available in all tests
    }

    // Reset clock mock if it exists
    try {
      val clock = testContext.applicationContext.getBean(Clock::class.java)
      if (Mockito.mockingDetails(clock).isMock) {
        Mockito.reset(clock)
      }
    } catch (e: Exception) {
      // Clock might not be mocked in all tests
    }
  }
}
