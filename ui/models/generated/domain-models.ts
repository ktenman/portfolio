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
    xirr: number | null;
    platforms: string[];
    priceChangeAmount: number | null;
    priceChangePercent: number | null;
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

export interface PortfolioSummaryDto {
    date: DateAsString;
    totalValue: number;
    xirrAnnualReturn: number;
    realizedProfit: number;
    unrealizedProfit: number;
    totalProfit: number;
    earningsPerDay: number;
    earningsPerMonth: number;
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
    ALPHA_VANTAGE = "ALPHA_VANTAGE",
    BINANCE = "BINANCE",
    FT = "FT",
}

export enum TransactionType {
    BUY = "BUY",
    SELL = "SELL",
}
