package ee.tenman.portfolio.configuration.aspect

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tenman.portfolio.configuration.TimeUtility
import jakarta.annotation.Resource
import lombok.extern.slf4j.Slf4j
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
@Slf4j
class LoggingAspect {

  @Resource
  private lateinit var objectMapper: ObjectMapper
  private val log = LoggerFactory.getLogger(LoggingAspect::class.java)

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
    val argsJson = objectMapper.writeValueAsString(joinPoint.getArgs())
    log.info("{} entered with arguments: {}", joinPoint.getSignature().toShortString(), argsJson)
  }

  @Throws(Throwable::class)
  private fun logExit(joinPoint: ProceedingJoinPoint, result: Any, startTime: Long) {
    val resultJson = objectMapper.writeValueAsString(result)
    log.info(
      "{} exited with result: {} in {} seconds",
      joinPoint.getSignature().toShortString(),
      resultJson,
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
