/* tslint:disable */
 
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
}

/**
 * Response containing instruments list with portfolio-wide XIRR
 */
export interface InstrumentsResponse {
    instruments: InstrumentDto[];
    portfolioXirr: number;
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
    holdingTicker: string | null;
    holdingName: string;
    percentageOfTotal: number;
    totalValueEur: number;
    holdingSector: string | null;
    inEtfs: string;
    numEtfs: number;
    platforms: string;
}

export interface CalculationResult extends Serializable {
    cashFlows: CashFlow[];
    median: number;
    average: number;
    total: number;
}

export interface Serializable {
}

export interface CashFlow extends Serializable {
    amount: number;
    date: DateAsString;
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
