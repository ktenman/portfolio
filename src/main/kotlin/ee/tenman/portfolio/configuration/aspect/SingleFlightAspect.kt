package ee.tenman.portfolio.configuration.aspect

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Aspect
@Component
class SingleFlightAspect {
  private val calls = ConcurrentHashMap<List<Any?>, CompletableFuture<Result<Any?>>>()

  @Around("@annotation(SingleFlight)")
  fun share(joinPoint: ProceedingJoinPoint): Any? {
    val key = listOf(joinPoint.signature.toLongString(), *joinPoint.args)
    val call = CompletableFuture<Result<Any?>>()
    calls.putIfAbsent(key, call)?.let { return it.join().getOrThrow() }
    val result = runCatching { joinPoint.proceed() }
    calls.remove(key, call)
    call.complete(result)
    return result.getOrThrow()
  }
}
