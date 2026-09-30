package ee.tenman.portfolio.testing.fixture

import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.service.etf.HoldingIdentityService
import io.mockk.every

fun HoldingIdentityService.answerPairs(answer: (IdentityPair) -> Boolean?) {
  every { resolveAll(any()) } answers { firstArg<Collection<IdentityPair>>().associateWith(answer) }
}
