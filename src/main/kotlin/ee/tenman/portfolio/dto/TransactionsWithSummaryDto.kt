package ee.tenman.portfolio.dto

data class TransactionsWithSummaryDto(
  val transactions: List<TransactionResponseDto>,
  val summary: TransactionSummaryDto,
)
