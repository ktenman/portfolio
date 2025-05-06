package e2e.retry

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class RetryExtension : TestExecutionExceptionHandler {
  private val log = LoggerFactory.getLogger(javaClass)
  private val attemptsPerTest = mutableMapOf<String, AtomicInteger>()

  override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
    val testMethod = context.requiredTestMethod
    val testClass = context.requiredTestClass

    // Get the Retry annotation from the method or class
    val retryAnnotation = testMethod.getAnnotation(Retry::class.java)
      ?: testClass.getAnnotation(Retry::class.java)
      ?: throw throwable

    // Get the exceptions to retry on
    val allowedExceptions = retryAnnotation.onExceptions.toList()

    // Check if current exception should be retried
    if (allowedExceptions.isEmpty() || allowedExceptions.any { it.isInstance(throwable) }) {
      val testName = "${testClass.name}.${testMethod.name}"
      val attempts = attemptsPerTest.computeIfAbsent(testName) { AtomicInteger(0) }
      val currentAttempt = attempts.incrementAndGet()

      if (currentAttempt <= retryAnnotation.times) {
        log.info(
          "Retrying test '{}' after failure (Attempt {}/{})",
          testName, currentAttempt, retryAnnotation.times
        )
        log.info("Failure was: {}: {}", throwable.javaClass.simpleName, throwable.message)
        return // Retry by not throwing
      }

      log.error("Test '{}' failed after {} attempts", testName, retryAnnotation.times)
    }

    throw throwable // Re-throw if no more retries or exception not in allowed list
  }
}
