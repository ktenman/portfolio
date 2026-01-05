package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_LOGOS_CACHE
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.util.UUID

class LogoCacheServiceTest {
  private val cacheManager = mockk<CacheManager>()
  private val cache = mockk<Cache>()
  private lateinit var service: LogoCacheService

  @BeforeEach
  fun setup() {
    service = LogoCacheService(cacheManager)
    every { cacheManager.getCache(ETF_LOGOS_CACHE) } returns cache
  }

  @Nested
  inner class GetLogo {
    @Test
    fun `should return logo data when cached`() {
      val uuid = UUID.randomUUID()
      val logoData = "test-logo".toByteArray()
      val wrapper = mockk<Cache.ValueWrapper>()
      every { cache.get(uuid.toString()) } returns wrapper
      every { wrapper.get() } returns logoData

      val result = service.getLogo(uuid)

      expect(result).toEqual(logoData)
    }

    @Test
    fun `should return null when not cached`() {
      val uuid = UUID.randomUUID()
      every { cache.get(uuid.toString()) } returns null

      val result = service.getLogo(uuid)

      expect(result).toEqual(null)
    }

    @Test
    fun `should return null when cache returns non-ByteArray`() {
      val uuid = UUID.randomUUID()
      val wrapper = mockk<Cache.ValueWrapper>()
      every { cache.get(uuid.toString()) } returns wrapper
      every { wrapper.get() } returns "not-a-byte-array"

      val result = service.getLogo(uuid)

      expect(result).toEqual(null)
    }

    @Test
    fun `should return null when cache is null`() {
      val uuid = UUID.randomUUID()
      every { cacheManager.getCache(ETF_LOGOS_CACHE) } returns null

      val result = service.getLogo(uuid)

      expect(result).toEqual(null)
    }
  }

  @Nested
  inner class SaveLogo {
    @Test
    fun `should save logo data to cache and return it`() {
      val uuid = UUID.randomUUID()
      val logoData = "test-logo".toByteArray()
      every { cache.put(uuid.toString(), logoData) } returns Unit

      val result = service.saveLogo(uuid, logoData)

      expect(result).toEqual(logoData)
      verify { cache.put(uuid.toString(), logoData) }
    }

    @Test
    fun `should handle null cache gracefully`() {
      val uuid = UUID.randomUUID()
      val logoData = "test-logo".toByteArray()
      every { cacheManager.getCache(ETF_LOGOS_CACHE) } returns null

      val result = service.saveLogo(uuid, logoData)

      expect(result).toEqual(logoData)
    }
  }

  @Nested
  inner class LogoExists {
    @Test
    fun `should return true when logo exists in cache`() {
      val uuid = UUID.randomUUID()
      val wrapper = mockk<Cache.ValueWrapper>()
      every { cache.get(uuid.toString()) } returns wrapper

      val result = service.logoExists(uuid)

      expect(result).toEqual(true)
    }

    @Test
    fun `should return false when logo does not exist in cache`() {
      val uuid = UUID.randomUUID()
      every { cache.get(uuid.toString()) } returns null

      val result = service.logoExists(uuid)

      expect(result).toEqual(false)
    }

    @Test
    fun `should return false when cache is null`() {
      val uuid = UUID.randomUUID()
      every { cacheManager.getCache(ETF_LOGOS_CACHE) } returns null

      val result = service.logoExists(uuid)

      expect(result).toEqual(false)
    }
  }

  @Nested
  inner class EvictLogo {
    @Test
    fun `should evict logo from cache`() {
      val uuid = UUID.randomUUID()
      every { cache.evict(uuid.toString()) } returns Unit

      service.evictLogo(uuid)

      verify { cache.evict(uuid.toString()) }
    }

    @Test
    fun `should handle null cache gracefully`() {
      val uuid = UUID.randomUUID()
      every { cacheManager.getCache(ETF_LOGOS_CACHE) } returns null

      service.evictLogo(uuid)
    }
  }
}
