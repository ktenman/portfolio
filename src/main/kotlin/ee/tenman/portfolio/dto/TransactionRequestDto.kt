package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.TransactionType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

data class TransactionRequestDto(
  val id: Long?,
  @field:NotNull(message = "Instrument ID is required")
  val instrumentId: Long,
  @field:NotNull(message = "Transaction type is required")
  val transactionType: TransactionType,
  @field:NotNull(message = "Quantity is required")
  @field:Positive(message = "Quantity must be positive")
  val quantity: BigDecimal,
  @field:NotNull(message = "Price is required")
  @field:Positive(message = "Price must be positive")
  val price: BigDecimal,
  @field:NotNull(message = "Transaction date is required")
  val transactionDate: LocalDate,
  @field:NotNull(message = "Platform is required")
  val platform: Platform,
  val commission: BigDecimal = BigDecimal.ZERO,
  @field:NotNull(message = "Currency is required")
  val currency: String = "EUR",
)
