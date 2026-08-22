package e2e.retry

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class RetryExtension : InvocationInterceptor {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    val retry = resolve(invocationContext.executable, extensionContext)
    if (retry == null) {
      invocation.proceed()
      return
    }
    invocation.skip()
    attempt(retry, invocationContext, extensionContext)
  }

  private fun resolve(
    method: Method,
    context: ExtensionContext,
  ): Retry? =
    method.getAnnotation(Retry::class.java)
      ?: context.requiredTestClass.getAnnotation(Retry::class.java)

  private fun attempt(
    retry: Retry,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    require(retry.times >= 1) { "Retry times must be at least 1 but was ${retry.times}" }
    val method = invocationContext.executable
    val target = invocationContext.target.orElseThrow()
    val arguments = invocationContext.arguments.toTypedArray()
    val name = "${extensionContext.requiredTestClass.simpleName}.${method.name}"
    var lastFailure: Throwable? = null
    repeat(retry.times) { index ->
      val outcome = runCatching { method.invoke(target, *arguments) }
      if (outcome.isSuccess) return
      val failure = checkNotNull(outcome.exceptionOrNull()) { "Attempt produced no failure for $name" }.unwrap()
      if (!retry.matches(failure)) throw failure
      lastFailure = failure
      log.info(
        "Test {} failed attempt {} of {}: {}",
        name,
        index + 1,
        retry.times,
        failure.message,
      )
    }
    throw lastFailure ?: IllegalStateException("Retry produced no outcome for $name")
  }
}

private fun Throwable.unwrap(): Throwable = (this as? InvocationTargetException)?.targetException ?: this

private fun Retry.matches(throwable: Throwable): Boolean = onExceptions.isEmpty() || onExceptions.any { it.isInstance(throwable) }
