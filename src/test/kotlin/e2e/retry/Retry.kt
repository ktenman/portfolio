package e2e.retry

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(
  val times: Int = 3,
  val onExceptions: Array<KClass<out Throwable>> = [Exception::class]
)
