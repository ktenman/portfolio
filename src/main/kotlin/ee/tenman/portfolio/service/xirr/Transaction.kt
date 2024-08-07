package ee.tenman.portfolio.service.xirr

import java.io.Serializable
import java.time.LocalDate

data class Transaction(val amount: Double, val date: LocalDate) : Serializable
