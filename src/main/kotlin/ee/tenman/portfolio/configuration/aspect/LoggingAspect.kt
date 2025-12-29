package ee.tenman.portfolio.configuration.aspect

import ee.tenman.portfolio.configuration.JsonMapperFactory
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
      log.warn("Loggable annotation should not be used on methods without return type: ${method.name}")
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
    val argsJson = JsonMapperFactory.instance.writeValueAsString(joinPoint.args)
    log.info("${joinPoint.signature.toShortString()} entered with arguments: $argsJson")
  }

  @Throws(Throwable::class)
  private fun logExit(
    joinPoint: ProceedingJoinPoint,
    result: Any,
    startTime: Long,
  ) {
    val resultJson = JsonMapperFactory.truncateJson(JsonMapperFactory.instance.writeValueAsString(result))
    val duration = TimeUtility.durationInSeconds(startTime)
    log.info("${joinPoint.signature.toShortString()} exited with result: $resultJson in $duration seconds")
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
