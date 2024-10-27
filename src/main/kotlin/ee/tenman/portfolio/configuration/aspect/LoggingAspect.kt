package ee.tenman.portfolio.configuration.aspect

import ee.tenman.portfolio.configuration.ObjectMapperConfig.Companion.OBJECT_MAPPER
import ee.tenman.portfolio.configuration.ObjectMapperConfig.Companion.truncateJson
import ee.tenman.portfolio.configuration.TimeUtility
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.*

@Aspect
@Component
class LoggingAspect {

  private val log = LoggerFactory.getLogger(javaClass)

  @Around("@annotation(Loggable)")
  @Throws(Throwable::class)
  fun logMethod(joinPoint: ProceedingJoinPoint): Any {
    val signature = joinPoint.signature as MethodSignature
    val method = signature.method

    if (method.returnType == Void.TYPE) {
      log.warn(
        "Loggable annotation should not be used on methods without return type: {}",
        method.name
      )
      return joinPoint.proceed()
    }

    return logInvocation(joinPoint)
  }

  @Throws(Throwable::class)
  private fun logInvocation(joinPoint: ProceedingJoinPoint): Any {
    val startTime = System.nanoTime()
    setTransactionId()
    try {
      logEntry(joinPoint)
      val result: Any = joinPoint.proceed()
      logExit(joinPoint, result, startTime)
      return result
    } finally {
      clearTransactionId()
    }
  }

  @Throws(Throwable::class)
  private fun logEntry(joinPoint: ProceedingJoinPoint) {
    val argsJson = OBJECT_MAPPER.writeValueAsString(joinPoint.args)
    log.info("{} entered with arguments: {}", joinPoint.signature.toShortString(), argsJson)
  }

  @Throws(Throwable::class)
  private fun logExit(joinPoint: ProceedingJoinPoint, result: Any, startTime: Long) {
    log.info(
      "{} exited with result: {} in {} seconds",
      joinPoint.signature.toShortString(),
      truncateJson(OBJECT_MAPPER.writeValueAsString(result)),
      TimeUtility.durationInSeconds(startTime)
    )
  }

  companion object {
    private const val TRANSACTION_ID = "transactionId"
    private fun setTransactionId() {
      val uuid = UUID.randomUUID()
      MDC.put(TRANSACTION_ID, String.format("[%s] ", uuid))
    }

    private fun clearTransactionId() {
      MDC.remove(TRANSACTION_ID)
    }
  }
}
