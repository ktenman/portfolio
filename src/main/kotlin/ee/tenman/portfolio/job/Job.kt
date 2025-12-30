package ee.tenman.portfolio.job

interface Job {
  fun execute()

  fun getName(): String = this::class.simpleName ?: "UnknownJob"
}
