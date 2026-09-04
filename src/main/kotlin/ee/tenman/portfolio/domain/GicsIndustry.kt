package ee.tenman.portfolio.domain

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
) {
  ENERGY_EQUIPMENT_AND_SERVICES(101010, "Energy Equipment & Services"),
  OIL_GAS_AND_CONSUMABLE_FUELS(101020, "Oil, Gas & Consumable Fuels"),
  CHEMICALS(151010, "Chemicals"),
  CONSTRUCTION_MATERIALS(151020, "Construction Materials"),
  CONTAINERS_AND_PACKAGING(151030, "Containers & Packaging"),
  METALS_AND_MINING(151040, "Metals & Mining"),
  PAPER_AND_FOREST_PRODUCTS(151050, "Paper & Forest Products"),
  AEROSPACE_AND_DEFENSE(201010, "Aerospace & Defense"),
  BUILDING_PRODUCTS(201020, "Building Products"),
  CONSTRUCTION_AND_ENGINEERING(201030, "Construction & Engineering"),
  ELECTRICAL_EQUIPMENT(201040, "Electrical Equipment"),
  INDUSTRIAL_CONGLOMERATES(201050, "Industrial Conglomerates"),
  MACHINERY(201060, "Machinery"),
  TRADING_COMPANIES_AND_DISTRIBUTORS(201070, "Trading Companies & Distributors"),
  COMMERCIAL_SERVICES_AND_SUPPLIES(202010, "Commercial Services & Supplies"),
  PROFESSIONAL_SERVICES(202020, "Professional Services"),
  AIR_FREIGHT_AND_LOGISTICS(203010, "Air Freight & Logistics"),
  PASSENGER_AIRLINES(203020, "Passenger Airlines"),
  MARINE_TRANSPORTATION(203030, "Marine Transportation"),
  GROUND_TRANSPORTATION(203040, "Ground Transportation"),
  TRANSPORTATION_INFRASTRUCTURE(203050, "Transportation Infrastructure"),
  AUTOMOBILE_COMPONENTS(251010, "Automobile Components"),
  AUTOMOBILES(251020, "Automobiles"),
  HOUSEHOLD_DURABLES(252010, "Household Durables"),
  LEISURE_PRODUCTS(252020, "Leisure Products"),
  TEXTILES_APPAREL_AND_LUXURY_GOODS(252030, "Textiles, Apparel & Luxury Goods"),
  HOTELS_RESTAURANTS_AND_LEISURE(253010, "Hotels, Restaurants & Leisure"),
  DIVERSIFIED_CONSUMER_SERVICES(253020, "Diversified Consumer Services"),
  DISTRIBUTORS(255010, "Distributors"),
  BROADLINE_RETAIL(255030, "Broadline Retail"),
  SPECIALTY_RETAIL(255040, "Specialty Retail"),
  CONSUMER_STAPLES_DISTRIBUTION_AND_RETAIL(301010, "Consumer Staples Distribution & Retail"),
  BEVERAGES(302010, "Beverages"),
  FOOD_PRODUCTS(302020, "Food Products"),
  TOBACCO(302030, "Tobacco"),
  HOUSEHOLD_PRODUCTS(303010, "Household Products"),
  PERSONAL_CARE_PRODUCTS(303020, "Personal Care Products"),
  HEALTH_CARE_EQUIPMENT_AND_SUPPLIES(351010, "Health Care Equipment & Supplies"),
  HEALTH_CARE_PROVIDERS_AND_SERVICES(351020, "Health Care Providers & Services"),
  HEALTH_CARE_TECHNOLOGY(351030, "Health Care Technology"),
  BIOTECHNOLOGY(352010, "Biotechnology"),
  PHARMACEUTICALS(352020, "Pharmaceuticals"),
  LIFE_SCIENCES_TOOLS_AND_SERVICES(352030, "Life Sciences Tools & Services"),
  BANKS(401010, "Banks"),
  FINANCIAL_SERVICES(402010, "Financial Services"),
  CONSUMER_FINANCE(402020, "Consumer Finance"),
  CAPITAL_MARKETS(402030, "Capital Markets"),
  MORTGAGE_REITS(402040, "Mortgage REITs"),
  INSURANCE(403010, "Insurance"),
  IT_SERVICES(451020, "IT Services"),
  SOFTWARE(451030, "Software"),
  COMMUNICATIONS_EQUIPMENT(452010, "Communications Equipment"),
  TECHNOLOGY_HARDWARE_STORAGE_AND_PERIPHERALS(452020, "Technology Hardware, Storage & Peripherals"),
  ELECTRONIC_EQUIPMENT_INSTRUMENTS_AND_COMPONENTS(452030, "Electronic Equipment, Instruments & Components"),
  SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT(453010, "Semiconductors & Semiconductor Equipment"),
  DIVERSIFIED_TELECOMMUNICATION_SERVICES(501010, "Diversified Telecommunication Services"),
  WIRELESS_TELECOMMUNICATION_SERVICES(501020, "Wireless Telecommunication Services"),
  MEDIA(502010, "Media"),
  ENTERTAINMENT(502020, "Entertainment"),
  INTERACTIVE_MEDIA_AND_SERVICES(502030, "Interactive Media & Services"),
  ELECTRIC_UTILITIES(551010, "Electric Utilities"),
  GAS_UTILITIES(551020, "Gas Utilities"),
  MULTI_UTILITIES(551030, "Multi-Utilities"),
  WATER_UTILITIES(551040, "Water Utilities"),
  INDEPENDENT_POWER_AND_RENEWABLE_ELECTRICITY_PRODUCERS(551050, "Independent Power and Renewable Electricity Producers"),
  DIVERSIFIED_REITS(601010, "Diversified REITs"),
  INDUSTRIAL_REITS(601025, "Industrial REITs"),
  HOTEL_AND_RESORT_REITS(601030, "Hotel & Resort REITs"),
  OFFICE_REITS(601040, "Office REITs"),
  HEALTH_CARE_REITS(601050, "Health Care REITs"),
  RESIDENTIAL_REITS(601060, "Residential REITs"),
  RETAIL_REITS(601070, "Retail REITs"),
  SPECIALIZED_REITS(601080, "Specialized REITs"),
  REAL_ESTATE_MANAGEMENT_AND_DEVELOPMENT(602010, "Real Estate Management & Development"),
  ;

  val sector: GicsSector get() = GicsSector.fromCode(code / 10000)

  val groupCode: Int get() = code / 100

  companion object {
    fun fromCode(code: Int): GicsIndustry? = entries.find { it.code == code }

    fun promptCatalogue(): String = entries.joinToString("\n") { "${it.code} ${it.displayName}" }
  }
}
