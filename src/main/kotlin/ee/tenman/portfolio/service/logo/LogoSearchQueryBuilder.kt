package ee.tenman.portfolio.service.logo

object LogoSearchQueryBuilder {
  fun buildQuery(
    name: String,
    ticker: String?,
  ): String = if (ticker.isNullOrBlank()) name else "$ticker $name"
}
