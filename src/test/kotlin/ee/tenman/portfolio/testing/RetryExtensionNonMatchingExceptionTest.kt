package ee.tenman.portfolio.testing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import e2e.retry.Retry
import e2e.retry.RetryExtension
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method
import java.util.Optional

class RetryExtensionNonMatchingExceptionTest {
  class NonMatchingExceptionTarget {
    var invocations = 0

    @Retry(times = 3, onExceptions = [IllegalStateException::class])
    fun fail() {
      invocations++
      throw IllegalArgumentException("attempt does not match onExceptions")
    }
  }

  @Test
  fun `should rethrow immediately and invoke the method exactly once when the exception does not match onExceptions`() {
    val target = NonMatchingExceptionTarget()
    val method = NonMatchingExceptionTarget::class.java.getDeclaredMethod("fail")
    val invocationContext = mockk<ReflectiveInvocationContext<Method>>()
    every { invocationContext.executable } returns method
    every { invocationContext.target } returns Optional.of(target)
    every { invocationContext.arguments } returns emptyList()
    val extensionContext = mockk<ExtensionContext>()
    every { extensionContext.requiredTestClass } returns NonMatchingExceptionTarget::class.java
    val invocation = mockk<InvocationInterceptor.Invocation<Void?>>()
    every { invocation.skip() } just runs

    expect {
      RetryExtension().interceptTestMethod(invocation, invocationContext, extensionContext)
    }.toThrow<IllegalArgumentException>()

    expect(target.invocations).toEqual(1)
  }
}
