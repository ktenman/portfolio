package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.ProcessResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class LightyearPriceUpdateService(
  private val priceUpdateProcessor: PriceUpdateProcessor,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun processSymbol(
    symbol: String,
    price: BigDecimal,
    isWeekend: Boolean,
    today: LocalDate,
  ): ProcessResult = priceUpdateProcessor.processSymbolUpdate(symbol, price, isWeekend, today, ProviderName.LIGHTYEAR)
}
