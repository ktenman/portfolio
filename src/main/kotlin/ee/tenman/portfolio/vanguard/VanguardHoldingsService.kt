package ee.tenman.portfolio.vanguard

import ee.tenman.portfolio.common.percentOf
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

@Service
class VanguardHoldingsService(
  private val vanguardHoldingsClient: VanguardHoldingsClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchHoldings(portId: String): VanguardFundSnapshot {
    val items = fetchAllItems(portId).filter { it.marketValuePercentage?.signum() != 0 }
    check(items.isNotEmpty()) { "Vanguard fund $portId returned no equity holdings" }
    warnAboutUnmappedIndustries(portId, items)
    val rows = parse(portId, items)
    val effectiveDate = resolveEffectiveDate(portId, rows)
    val issuers = mergeByIssuer(rows)
    val holdings = normalize(portId, issuers)
    log.info("Fetched ${holdings.size} Vanguard issuers from ${rows.size} rows for fund $portId effective $effectiveDate")
    return VanguardFundSnapshot(effectiveDate, holdings, issuers.associate { it.name to it.countryCodes })
  }

  private fun fetchAllItems(portId: String): List<VanguardHoldingItem> {
    val pages =
      generateSequence(fetchPage(portId, null)) { previous ->
        previous.lastItemKey?.let { fetchPage(portId, it) }
      }.take(MAX_PAGES + 1).toList()
    check(pages.size <= MAX_PAGES) { "Vanguard fund $portId returned more than $MAX_PAGES holdings pages" }
    return pages.flatMap { it.items.orEmpty() }
  }

  private fun fetchPage(
    portId: String,
    lastItemKey: String?,
  ): VanguardHoldingsPage {
    val request =
      VanguardHoldingsRequest(
        query = HOLDINGS_QUERY,
        variables = VanguardHoldingsVariables(listOf(portId), SECURITY_TYPES, lastItemKey),
      )
    val response = vanguardHoldingsClient.getHoldings(request)
    val errors = response.errors.orEmpty()
    check(errors.isEmpty()) { "Vanguard fund $portId returned GraphQL errors ${errors.mapNotNull { it.message }}" }
    return response.data
      ?.borHoldings
      ?.firstOrNull()
      ?.holdings
      ?: error("Vanguard fund $portId returned no holdings payload")
  }

  private fun parse(
    portId: String,
    items: List<VanguardHoldingItem>,
  ): List<VanguardHolding> =
    items.map { item ->
      val name = item.issuerName?.trim()
      check(!name.isNullOrEmpty()) { "Vanguard fund $portId returned a holding without an issuer name" }
      val weight = item.marketValuePercentage ?: error("Vanguard fund $portId returned holding '$name' without a weight")
      val effectiveDate = item.effectiveDate ?: error("Vanguard fund $portId returned holding '$name' without an effective date")
      VanguardHolding(
        name = name,
        ticker = item.ticker?.trim()?.takeIf { it.isNotEmpty() },
        weight = weight,
        effectiveDate = effectiveDate,
        industry = industryOf(item),
        countryCodes =
          setOfNotNull(
            item.bloombergIsoCountry
          ?.trim()
          ?.uppercase(Locale.ROOT)
          ?.takeIf { it in COUNTRY_CODES },
              ),
          )
    }

  private fun resolveEffectiveDate(
    portId: String,
    rows: List<VanguardHolding>,
  ): LocalDate {
    val dates = rows.map { it.effectiveDate }.distinct().sorted()
    return dates.singleOrNull() ?: error("Vanguard fund $portId returned mixed effective dates $dates")
  }

  private fun mergeByIssuer(rows: List<VanguardHolding>): List<VanguardHolding> =
    rows
      .groupBy { it.name.lowercase() }
      .values
      .map { shareClasses -> mergeShareClasses(shareClasses) }

  private fun mergeShareClasses(shareClasses: List<VanguardHolding>): VanguardHolding {
    val ordered = shareClasses.sortedWith(compareByDescending<VanguardHolding> { it.weight }.thenBy { it.ticker ?: "" })
    val industries = shareClasses.mapNotNull { it.industry }.distinct()
    check(industries.size <= 1) { "Vanguard issuer '${ordered.first().name}' returned conflicting industries $industries" }
    return ordered.first().copy(
      weight = shareClasses.sumOf { it.weight },
      industry = industries.singleOrNull(),
      countryCodes = shareClasses.flatMap { it.countryCodes }.toSet(),
    )
  }

  private fun normalize(
    portId: String,
    issuers: List<VanguardHolding>,
  ): List<HoldingData> {
    val total = issuers.sumOf { it.weight }
    check(total > BigDecimal.ZERO) { "Vanguard fund $portId returned a total weight of $total" }
    return issuers
      .sortedWith(compareByDescending<VanguardHolding> { it.weight }.thenBy { it.name })
      .mapIndexed { index, issuer ->
        HoldingData(
          name = issuer.name,
          ticker = issuer.ticker,
          sector = null,
          weight = issuer.weight.percentOf(total, CALCULATION_SCALE),
          rank = index + 1,
          industry = issuer.industry,
        )
      }
  }

  private fun industryOf(item: VanguardHoldingItem): GicsIndustry? =
    item.gicsIndustryDescription
      ?.trim()
      ?.let { INDUSTRIES_BY_DESCRIPTION[it] }

  private fun warnAboutUnmappedIndustries(
    portId: String,
    items: List<VanguardHoldingItem>,
  ) {
    val unmapped =
      items
        .mapNotNull { it.gicsIndustryDescription?.trim() }
        .filter { it.isNotEmpty() && it !in INDUSTRIES_BY_DESCRIPTION }
        .distinct()
    if (unmapped.isEmpty()) return
    log.warn("Vanguard fund $portId returned unmapped GICS industries $unmapped")
  }

  companion object {
    val FUNDS =
      mapOf(
        "VGLA:GER:EUR" to "E161",
        "VXUS:GER:EUR" to "E165",
        "VUAA:GER:EUR" to "9694",
        "VWCE:GER:EUR" to "9679",
        "VNRA:GER:EUR" to "9680",
        "VNRT:AEX:EUR" to "9523",
        "VWCG:GER:EUR" to "9681",
      )
    private const val MAX_PAGES = 20
    private const val PAGE_LIMIT = 1500
    private val COUNTRY_CODES = Locale.getISOCountries().toSet()

    private val SECURITY_TYPES =
      listOf("EQ.DRCPT", "EQ.ETF", "EQ.FSH", "EQ.PREF", "EQ.PSH", "EQ.REIT", "EQ.STOCK", "EQ.RIGHT", "EQ.WRT")

    private val INDUSTRIES_BY_DESCRIPTION =
      GicsIndustry.entries.associateBy { it.displayName } +
        mapOf("Mortgage Real Estate Investment Trusts (REITs)" to GicsIndustry.MORTGAGE_REITS)

    private val HOLDINGS_QUERY =
      """
      query FundHoldings(${'$'}portIds: [String!], ${'$'}securityTypes: [String!], ${'$'}lastItemKey: String) {
        borHoldings(portIds: ${'$'}portIds) {
          holdings(limit: $PAGE_LIMIT, securityTypes: ${'$'}securityTypes, lastItemKey: ${'$'}lastItemKey) {
            items { issuerName ticker marketValuePercentage gicsIndustryDescription bloombergIsoCountry effectiveDate }
            lastItemKey
          }
        }
      }
      """.trimIndent()
  }
}
