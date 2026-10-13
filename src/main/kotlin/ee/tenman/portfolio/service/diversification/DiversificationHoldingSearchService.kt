package ee.tenman.portfolio.service.diversification

import ee.tenman.portfolio.dto.DiversificationCalculatorRequestDto
import ee.tenman.portfolio.dto.DiversificationHoldingDto
import ee.tenman.portfolio.dto.DiversificationHoldingSearchRequestDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.text.Normalizer

@Service
class DiversificationHoldingSearchService(
  private val calculator: DiversificationCalculatorService,
) {
  fun search(request: DiversificationHoldingSearchRequestDto): Page<DiversificationHoldingDto> {
    val query = normalize(request.query)
    val holdings =
      calculator
        .calculate(DiversificationCalculatorRequestDto(request.allocations))
        .holdings
        .filter { query.isEmpty() || matches(it, query) }
        .sortedWith(compareByDescending<DiversificationHoldingDto> { it.percentage }.thenBy { it.name })
    val pageable = PageRequest.of(request.page, PAGE_SIZE)
    val offset = pageable.offset.coerceAtMost(holdings.size.toLong()).toInt()
    return PageImpl(holdings.drop(offset).take(PAGE_SIZE), pageable, holdings.size.toLong())
  }

  private fun matches(
    holding: DiversificationHoldingDto,
    query: String,
  ): Boolean = normalize(holding.name).contains(query) || holding.ticker?.let { normalize(it).contains(query) } == true

  private fun normalize(value: String): String =
    Normalizer
      .normalize(value, Normalizer.Form.NFD)
      .replace(COMBINING_MARKS, "")
      .lowercase()
      .replace(WHITESPACE, " ")
      .trim()

  companion object {
    private const val PAGE_SIZE = 100
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("[\\s\\p{Z}]+")
  }
}
