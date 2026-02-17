/* tslint:disable */
/* eslint-disable */
// Generated using typescript-generator (timestamp removed to prevent git churn)

/**
 * Financial instrument data transfer object
 */
export interface InstrumentDto {
    id: number | null;
    symbol: string;
    name: string;
    category: string;
    baseCurrency: string;
    currentPrice: number | null;
    quantity: number | null;
    providerName: string;
    totalInvestment: number | null;
    currentValue: number | null;
    profit: number | null;
    realizedProfit: number | null;
    unrealizedProfit: number | null;
    xirr: number | null;
    platforms: string[];
    priceChangeAmount: number | null;
    priceChangePercent: number | null;
    ter: number | null;
    xirrAnnualReturn: number | null;
    firstTransactionDate: DateAsString | null;
}

/**
 * Response containing instruments list with portfolio-wide XIRR
 */
export interface InstrumentsResponse {
    instruments: InstrumentDto[];
    portfolioXirr: number | null;
}

export interface TransactionRequestDto {
    id: number | null;
    instrumentId: number;
    transactionType: TransactionType;
    quantity: number;
    price: number;
    transactionDate: DateAsString;
    platform: Platform;
    commission: number;
    currency: string;
}

export interface TransactionResponseDto {
    id: number | null;
    instrumentId: number;
    symbol: string;
    name: string;
    transactionType: TransactionType;
    quantity: number;
    price: number;
    transactionDate: DateAsString;
    platform: Platform;
    realizedProfit: number | null;
    unrealizedProfit: number;
    averageCost: number | null;
    remainingQuantity: number;
    commission: number;
    currency: string;
}

export interface TransactionSummaryDto {
    totalRealizedProfit: number;
    totalUnrealizedProfit: number;
    totalProfit: number;
    totalInvested: number;
}

export interface TransactionsWithSummaryDto {
    transactions: TransactionResponseDto[];
    summary: TransactionSummaryDto;
}

export interface PortfolioSummaryDto {
    date: DateAsString;
    totalValue: number;
    xirrAnnualReturn: number;
    realizedProfit: number;
    unrealizedProfit: number;
    totalProfit: number;
    earningsPerDay: number;
    earningsPerMonth: number;
    totalProfitChange24h: number | null;
}

export interface EtfHoldingBreakdownDto extends Serializable {
    holdingUuid: string | null;
    holdingTicker: string | null;
    holdingName: string;
    percentageOfTotal: number;
    totalValueEur: number;
    holdingSector: string | null;
    holdingCountryCode: string | null;
    holdingCountryName: string | null;
    inEtfs: string;
    numEtfs: number;
    platforms: string;
}

export interface EtfDiagnosticDto {
    instrumentId: number;
    symbol: string;
    providerName: ProviderName;
    currentPrice: number | null;
    etfPositionCount: number;
    latestSnapshotDate: string | null;
    transactionCount: number;
    netQuantity: number;
    hasEtfHoldings: boolean;
    hasActivePosition: boolean;
    platforms: string[];
}

export interface CalculationResult extends Serializable {
    cashFlows: CashFlow[];
    median: number;
    average: number;
    total: number;
}

export interface DiversificationCalculatorRequestDto {
    allocations: AllocationDto[];
}

export interface DiversificationCalculatorResponseDto extends Serializable {
    weightedTer: number;
    weightedAnnualReturn: number;
    totalUniqueHoldings: number;
    holdings: DiversificationHoldingDto[];
    sectors: DiversificationSectorDto[];
    countries: DiversificationCountryDto[];
    concentration: ConcentrationDto;
}

export interface EtfDetailDto extends Serializable {
    instrumentId: number;
    symbol: string;
    name: string;
    allocation: number;
    ter: number | null;
    annualReturn: number | null;
    currentPrice: number | null;
}

export interface ReturnPredictionDto extends Serializable {
    currentValue: number;
    xirrAnnualReturn: number;
    dailyVolatility: number;
    dataPointCount: number;
    predictions: HorizonPredictionDto[];
}

export interface HorizonPredictionDto extends Serializable {
    horizon: string;
    horizonDays: number;
    targetDate: DateAsString;
    xirrProjectedValue: number;
    expectedValue: number;
    optimisticValue: number;
    pessimisticValue: number;
}

export interface Serializable {
}

export interface CashFlow extends Serializable {
    amount: number;
    date: DateAsString;
}

export interface AllocationDto {
    instrumentId: number;
    percentage: number;
}

export interface DiversificationHoldingDto extends Serializable {
    name: string;
    ticker: string | null;
    percentage: number;
    inEtfs: string;
}

export interface DiversificationSectorDto extends Serializable {
    sector: string;
    percentage: number;
}

export interface DiversificationCountryDto extends Serializable {
    countryCode: string | null;
    countryName: string;
    percentage: number;
}

export interface ConcentrationDto extends Serializable {
    top10Percentage: number;
    largestPosition: LargestPositionDto | null;
}

export interface LargestPositionDto extends Serializable {
    name: string;
    percentage: number;
}

type DateAsString = string;

export enum Platform {
    AVIVA = "AVIVA",
    BINANCE = "BINANCE",
    COINBASE = "COINBASE",
    IBKR = "IBKR",
    LHV = "LHV",
    LIGHTYEAR = "LIGHTYEAR",
    SWEDBANK = "SWEDBANK",
    TRADING212 = "TRADING212",
    UNKNOWN = "UNKNOWN",
}

export enum ProviderName {
    BINANCE = "BINANCE",
    FT = "FT",
    LIGHTYEAR = "LIGHTYEAR",
    MANUAL = "MANUAL",
    SYNTHETIC = "SYNTHETIC",
    TRADING212 = "TRADING212",
}

export enum TransactionType {
    BUY = "BUY",
    SELL = "SELL",
}

export enum PriceChangePeriod {
    P24H = "P24H",
    P48H = "P48H",
    P3D = "P3D",
    P7D = "P7D",
    P10D = "P10D",
    P30D = "P30D",
    P1Y = "P1Y",
}
