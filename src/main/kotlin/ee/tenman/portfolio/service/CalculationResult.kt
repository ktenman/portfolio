package ee.tenman.portfolio.service

import ee.tenman.portfolio.service.xirr.Transaction
import java.io.Serializable

data class CalculationResult(
  var xirrs: List<Transaction> = mutableListOf(),
  var median: Double = 0.0,
  var average: Double = 0.0
) : Serializable
