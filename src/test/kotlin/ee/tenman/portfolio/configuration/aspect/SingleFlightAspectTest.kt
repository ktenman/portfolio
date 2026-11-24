package ee.tenman.portfolio.configuration.aspect

import ch.tutteli.atrium.api.fluent.en_GB.cause
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightAspectTest {
  @Test
  fun `computes once for identical calls in flight`() {
    expect(follow(SlowLookup(), "Žürich").get(5, TimeUnit.SECONDS)).toEqual("Žürich-1")
  }

  @Test
  fun `rethrows the leader failure to identical calls in flight`() {
    val follower = follow(SlowLookup(failing = true), "Õismäe")
    expect { follower.get(5, TimeUnit.SECONDS) }.toThrow<ExecutionException>().cause<IllegalStateException>()
  }

  @Test
  fun `computes separately for different arguments in flight`() {
    val lookup = SlowLookup(arrivals = 2)
    val proxy = proxied(lookup)
    started { proxy.find("Žürich") }
    started { proxy.find("Õismäe") }
    val both = lookup.entered.await(5, TimeUnit.SECONDS)
    lookup.release.countDown()
    expect(both).toEqual(true)
  }

  @Test
  fun `computes again once the earlier identical call has finished`() {
    val proxy = proxied(SlowLookup().apply { release.countDown() })
    proxy.find("Žürich")
    expect(proxy.find("Žürich")).toEqual("Žürich-2")
  }

  private fun follow(
    lookup: SlowLookup,
    name: String,
  ): FutureTask<String> {
    val proxy = proxied(lookup)
    started { proxy.find(name) }
    lookup.entered.await(5, TimeUnit.SECONDS)
    val follower = FutureTask { proxy.find(name) }
    val thread = Thread(follower).apply { start() }
    await()
      .atMost(5, TimeUnit.SECONDS)
      .pollInterval(Duration.ofMillis(10))
      .until { thread.state in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING) }
    lookup.release.countDown()
    return follower
  }

  private fun proxied(lookup: SlowLookup): SlowLookup = AspectJProxyFactory(lookup).apply { addAspect(SingleFlightAspect()) }.getProxy()

  private fun started(block: () -> String): FutureTask<String> = FutureTask(block).also { Thread(it).start() }
}

open class SlowLookup(
  arrivals: Int = 1,
  private val failing: Boolean = false,
) {
  private val calls = AtomicInteger()
  val entered = CountDownLatch(arrivals)
  val release = CountDownLatch(1)

  @SingleFlight
  open fun find(name: String): String {
    val call = calls.incrementAndGet()
    entered.countDown()
    release.await(5, TimeUnit.SECONDS)
    check(!failing || call > 1) { "Lookup of $name failed" }
    return "$name-$call"
  }
}
