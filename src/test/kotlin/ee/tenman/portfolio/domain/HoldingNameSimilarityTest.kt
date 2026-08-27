package ee.tenman.portfolio.domain

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class HoldingNameSimilarityTest {
  @Test
  fun `should reject companies sharing only one word`() {
    expect(HoldingNameSimilarity.mayBeSameCompany("China Merchants Bank", "China Life Insurance")).toEqual(false)
  }

  @Test
  fun `should accept names differing only by share class and legal form`() {
    expect(HoldingNameSimilarity.mayBeSameCompany("Sensetime Group Class B Inc", "Sensetime Group Inc")).toEqual(true)
  }

  @Test
  fun `should accept a candidate truncated mid word`() {
    expect(
      HoldingNameSimilarity.mayBeSameCompany("Zhejiang Sanhua Intelligent Controls Co Ltd", "Zhejiang Sanhua Intelligen-h"),
    ).toEqual(true)
  }

  @Test
  fun `should accept an abbreviation sharing no whole word`() {
    expect(HoldingNameSimilarity.mayBeSameCompany("GSK", "GlaxoSmithKline")).toEqual(true)
  }

  @Test
  fun `should ignore diacritics when comparing tokens`() {
    expect(HoldingNameSimilarity.mayBeSameCompany("ASML Hōlding Group", "ASML Holding Group")).toEqual(true)
  }

  @Test
  fun `should reject companies whose only shared token is a legal form`() {
    expect(HoldingNameSimilarity.mayBeSameCompany("Adani Ports Ltd", "Reliance Power Ltd")).toEqual(false)
  }

  @Test
  fun `should accept two letter tokens only when they match exactly`() {
    expect(HoldingNameSimilarity.mayBeSameCompany("Hp Enterprise Services", "Hp Enterprise Solutions")).toEqual(true)
  }
}
