package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.HoldingBlockKey
import org.junit.jupiter.api.Test

class HoldingBlockKeyTest {
  @Test
  fun `should take first token ignoring legal suffix`() {
    expect(HoldingBlockKey.of("NVIDIA CORP")).toEqual("nvidia")
  }

  @Test
  fun `should produce the same block key for all caps and mixed case variants`() {
    expect(HoldingBlockKey.of("NVIDIA")).toEqual(HoldingBlockKey.of("Nvidia Corporation"))
  }

  @Test
  fun `should split on punctuation when name has no spaces before suffix`() {
    expect(HoldingBlockKey.of("Amazon.com Inc")).toEqual("amazon")
  }

  @Test
  fun `should group different companies sharing a first word under one block key`() {
    expect(HoldingBlockKey.of("Merck & Co.")).toEqual(HoldingBlockKey.of("Merck KGaA"))
  }

  @Test
  fun `should keep leading digits in the block key`() {
    expect(HoldingBlockKey.of("3M Company")).toEqual("3m")
  }

  @Test
  fun `should skip leading non ascii characters`() {
    expect(HoldingBlockKey.of("Évolution SA")).toEqual("volution")
  }

  @Test
  fun `should fold dotted capital i to plain ascii i matching database lowercase`() {
    expect(HoldingBlockKey.of("İstanbul Holding")).toEqual("istanbul")
  }

  @Test
  fun `should return empty string when name has no alphanumeric characters`() {
    expect(HoldingBlockKey.of("—")).toEqual("")
  }
}
