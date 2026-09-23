package ee.tenman.portfolio.domain

import java.time.LocalDate
import java.util.UUID

enum class IndustrySource {
  LLM,
  VANGUARD,
  UNKNOWN,
}

data class VanguardIndustryUpdate(
  val holdingUuid: UUID,
  val industry: GicsIndustry,
  val effectiveDate: LocalDate,
)

enum class GicsSector(
  val code: Int,
  val displayName: String,
) {
  ENERGY(10, "Energy"),
  MATERIALS(15, "Materials"),
  INDUSTRIALS(20, "Industrials"),
  CONSUMER_DISCRETIONARY(25, "Consumer Discretionary"),
  CONSUMER_STAPLES(30, "Consumer Staples"),
  HEALTH_CARE(35, "Health Care"),
  FINANCIALS(40, "Financials"),
  INFORMATION_TECHNOLOGY(45, "Information Technology"),
  COMMUNICATION_SERVICES(50, "Communication Services"),
  UTILITIES(55, "Utilities"),
  REAL_ESTATE(60, "Real Estate"),
  ;

  companion object {
    fun fromCode(code: Int): GicsSector = entries.first { it.code == code }
  }
}

enum class GicsIndustry(
  val code: Int,
  val displayName: String,
  val industrySector: IndustrySector,
) {
  ENERGY_EQUIPMENT_AND_SERVICES(101010, "Energy Equipment & Services", IndustrySector.ENERGY),
  OIL_GAS_AND_CONSUMABLE_FUELS(101020, "Oil, Gas & Consumable Fuels", IndustrySector.ENERGY),
  CHEMICALS(151010, "Chemicals", IndustrySector.INDUSTRIALS),
  CONSTRUCTION_MATERIALS(151020, "Construction Materials", IndustrySector.INDUSTRIALS),
  CONTAINERS_AND_PACKAGING(151030, "Containers & Packaging", IndustrySector.INDUSTRIALS),
  METALS_AND_MINING(151040, "Metals & Mining", IndustrySector.INDUSTRIALS),
  PAPER_AND_FOREST_PRODUCTS(151050, "Paper & Forest Products", IndustrySector.INDUSTRIALS),
  AEROSPACE_AND_DEFENSE(201010, "Aerospace & Defense", IndustrySector.INDUSTRIALS),
  BUILDING_PRODUCTS(201020, "Building Products", IndustrySector.INDUSTRIALS),
  CONSTRUCTION_AND_ENGINEERING(201030, "Construction & Engineering", IndustrySector.INDUSTRIALS),
  ELECTRICAL_EQUIPMENT(201040, "Electrical Equipment", IndustrySector.INDUSTRIALS),
  INDUSTRIAL_CONGLOMERATES(201050, "Industrial Conglomerates", IndustrySector.INDUSTRIALS),
  MACHINERY(201060, "Machinery", IndustrySector.INDUSTRIALS),
  TRADING_COMPANIES_AND_DISTRIBUTORS(201070, "Trading Companies & Distributors", IndustrySector.INDUSTRIALS),
  COMMERCIAL_SERVICES_AND_SUPPLIES(202010, "Commercial Services & Supplies", IndustrySector.BUSINESS_SERVICES),
  PROFESSIONAL_SERVICES(202020, "Professional Services", IndustrySector.BUSINESS_SERVICES),
  AIR_FREIGHT_AND_LOGISTICS(203010, "Air Freight & Logistics", IndustrySector.MOBILITY),
  PASSENGER_AIRLINES(203020, "Passenger Airlines", IndustrySector.MOBILITY),
  MARINE_TRANSPORTATION(203030, "Marine Transportation", IndustrySector.MOBILITY),
  GROUND_TRANSPORTATION(203040, "Ground Transportation", IndustrySector.MOBILITY),
  TRANSPORTATION_INFRASTRUCTURE(203050, "Transportation Infrastructure", IndustrySector.MOBILITY),
  AUTOMOBILE_COMPONENTS(251010, "Automobile Components", IndustrySector.MOBILITY),
  AUTOMOBILES(251020, "Automobiles", IndustrySector.MOBILITY),
  HOUSEHOLD_DURABLES(252010, "Household Durables", IndustrySector.CONSUMER_DISCRETIONARY),
  LEISURE_PRODUCTS(252020, "Leisure Products", IndustrySector.CONSUMER_DISCRETIONARY),
  TEXTILES_APPAREL_AND_LUXURY_GOODS(252030, "Textiles, Apparel & Luxury Goods", IndustrySector.CONSUMER_DISCRETIONARY),
  HOTELS_RESTAURANTS_AND_LEISURE(253010, "Hotels, Restaurants & Leisure", IndustrySector.CONSUMER_DISCRETIONARY),
  DIVERSIFIED_CONSUMER_SERVICES(253020, "Diversified Consumer Services", IndustrySector.CONSUMER_DISCRETIONARY),
  DISTRIBUTORS(255010, "Distributors", IndustrySector.INDUSTRIALS),
  BROADLINE_RETAIL(255030, "Broadline Retail", IndustrySector.CONSUMER_ESSENTIALS),
  SPECIALTY_RETAIL(255040, "Specialty Retail", IndustrySector.CONSUMER_DISCRETIONARY),
  CONSUMER_STAPLES_DISTRIBUTION_AND_RETAIL(301010, "Consumer Staples Distribution & Retail", IndustrySector.CONSUMER_ESSENTIALS),
  BEVERAGES(302010, "Beverages", IndustrySector.CONSUMER_ESSENTIALS),
  FOOD_PRODUCTS(302020, "Food Products", IndustrySector.CONSUMER_ESSENTIALS),
  TOBACCO(302030, "Tobacco", IndustrySector.CONSUMER_ESSENTIALS),
  HOUSEHOLD_PRODUCTS(303010, "Household Products", IndustrySector.CONSUMER_ESSENTIALS),
  PERSONAL_CARE_PRODUCTS(303020, "Personal Care Products", IndustrySector.CONSUMER_ESSENTIALS),
  HEALTH_CARE_EQUIPMENT_AND_SUPPLIES(351010, "Health Care Equipment & Supplies", IndustrySector.HEALTH),
  HEALTH_CARE_PROVIDERS_AND_SERVICES(351020, "Health Care Providers & Services", IndustrySector.HEALTH),
  HEALTH_CARE_TECHNOLOGY(351030, "Health Care Technology", IndustrySector.HEALTH),
  BIOTECHNOLOGY(352010, "Biotechnology", IndustrySector.HEALTH),
  PHARMACEUTICALS(352020, "Pharmaceuticals", IndustrySector.HEALTH),
  LIFE_SCIENCES_TOOLS_AND_SERVICES(352030, "Life Sciences Tools & Services", IndustrySector.HEALTH),
  BANKS(401010, "Banks", IndustrySector.FINANCE),
  FINANCIAL_SERVICES(402010, "Financial Services", IndustrySector.FINANCE),
  CONSUMER_FINANCE(402020, "Consumer Finance", IndustrySector.FINANCE),
  CAPITAL_MARKETS(402030, "Capital Markets", IndustrySector.FINANCE),
  MORTGAGE_REITS(402040, "Mortgage REITs", IndustrySector.FINANCE),
  INSURANCE(403010, "Insurance", IndustrySector.FINANCE),
  IT_SERVICES(451020, "IT Services", IndustrySector.BUSINESS_SERVICES),
  SOFTWARE(451030, "Software", IndustrySector.SOFTWARE_CLOUD_SERVICES),
  COMMUNICATIONS_EQUIPMENT(452010, "Communications Equipment", IndustrySector.COMMUNICATION),
  TECHNOLOGY_HARDWARE_STORAGE_AND_PERIPHERALS(452020, "Technology Hardware, Storage & Peripherals", IndustrySector.DIGITAL_HARDWARE),
  ELECTRONIC_EQUIPMENT_INSTRUMENTS_AND_COMPONENTS(
    452030,
    "Electronic Equipment, Instruments & Components",
    IndustrySector.DIGITAL_HARDWARE,
  ),
  SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT(453010, "Semiconductors & Semiconductor Equipment", IndustrySector.SEMICONDUCTORS),
  DIVERSIFIED_TELECOMMUNICATION_SERVICES(501010, "Diversified Telecommunication Services", IndustrySector.COMMUNICATION),
  WIRELESS_TELECOMMUNICATION_SERVICES(501020, "Wireless Telecommunication Services", IndustrySector.COMMUNICATION),
  MEDIA(502010, "Media", IndustrySector.COMMUNICATION),
  ENTERTAINMENT(502020, "Entertainment", IndustrySector.CONSUMER_DISCRETIONARY),
  INTERACTIVE_MEDIA_AND_SERVICES(502030, "Interactive Media & Services", IndustrySector.SOFTWARE_CLOUD_SERVICES),
  ELECTRIC_UTILITIES(551010, "Electric Utilities", IndustrySector.UTILITIES),
  GAS_UTILITIES(551020, "Gas Utilities", IndustrySector.UTILITIES),
  MULTI_UTILITIES(551030, "Multi-Utilities", IndustrySector.UTILITIES),
  WATER_UTILITIES(551040, "Water Utilities", IndustrySector.UTILITIES),
  INDEPENDENT_POWER_AND_RENEWABLE_ELECTRICITY_PRODUCERS(
    551050,
    "Independent Power and Renewable Electricity Producers",
    IndustrySector.ENERGY,
  ),
  DIVERSIFIED_REITS(601010, "Diversified REITs", IndustrySector.FINANCE),
  INDUSTRIAL_REITS(601025, "Industrial REITs", IndustrySector.FINANCE),
  HOTEL_AND_RESORT_REITS(601030, "Hotel & Resort REITs", IndustrySector.FINANCE),
  OFFICE_REITS(601040, "Office REITs", IndustrySector.FINANCE),
  HEALTH_CARE_REITS(601050, "Health Care REITs", IndustrySector.FINANCE),
  RESIDENTIAL_REITS(601060, "Residential REITs", IndustrySector.FINANCE),
  RETAIL_REITS(601070, "Retail REITs", IndustrySector.FINANCE),
  SPECIALIZED_REITS(601080, "Specialized REITs", IndustrySector.BUSINESS_SERVICES),
  REAL_ESTATE_MANAGEMENT_AND_DEVELOPMENT(602010, "Real Estate Management & Development", IndustrySector.FINANCE),
  ;

  val sector: GicsSector get() = GicsSector.fromCode(code / 10000)

  val groupCode: Int get() = code / 100

  companion object {
    fun fromCode(code: Int): GicsIndustry? = entries.find { it.code == code }

    fun promptCatalogue(): String = entries.joinToString("\n") { "${it.code} ${it.displayName}" }
  }
}
