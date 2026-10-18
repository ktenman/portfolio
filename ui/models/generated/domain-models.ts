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
    fundCurrency: Currency | null;
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
    netInvested: number;
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

export interface XirrWindowDto {
    period: string;
    fromDate: DateAsString | null;
    xirr: number | null;
}

export interface XirrWindowsDto {
    windows: XirrWindowDto[];
}

export interface AnnualWindowDto {
    period: string;
    fromDate: DateAsString | null;
    annualReturn: number | null;
}

export interface AnnualWindowsDto {
    windows: AnnualWindowDto[];
}

export interface RangeChangeDto {
    changeAmount: number;
    changePercent: number;
}

export interface IntradaySummaryPointDto extends Serializable {
    date: DateAsString;
    totalValue: number;
    xirrAnnualReturn: number;
    totalProfit: number;
    earningsPerMonth: number;
}

export interface BenchmarkPointDto extends Serializable {
    date: DateAsString;
    price: number;
}

export interface EtfHoldingBreakdownDto extends Serializable {
    holdingUuid: string | null;
    holdingTicker: string | null;
    holdingName: string;
    percentageOfTotal: number;
    totalValueEur: number;
    holdingSector: string | null;
    holdingIndustry: string | null;
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
    industries: DiversificationIndustryDto[];
    countries: DiversificationCountryDto[];
    concentration: ConcentrationDto;
}

export interface DiversificationHoldingSearchRequestDto {
    allocations: AllocationDto[];
    query: string;
    page: number;
}

export interface EtfDetailDto extends Serializable {
    instrumentId: number;
    symbol: string;
    name: string;
    allocation: number;
    ter: number | null;
    annualReturn: number | null;
    currentPrice: number | null;
    fundCurrency: Currency | null;
}

export interface FundReportDto {
    asOfDate: DateAsString;
    funds: FundWeightDto[];
}

export interface CollectionStatusDto {
    key: string;
    provider: string;
    operation: string;
    job: string;
    status: CollectionStatus;
    expected: number;
    fetched: number;
    persisted: number;
    failed: number;
    failedItems: string[];
    items: CollectionItemStatusDto[];
    consecutiveEmptyRuns: number;
    durationSeconds: number;
    lastAttempt: DateAsString | null;
    lastCompletion: DateAsString | null;
    lastFullSuccess: DateAsString | null;
    deadline: DateAsString | null;
    breakerOpen: boolean;
    error: string | null;
}

export interface CollectionItemStatusDto {
    symbol: string;
    lastSuccess: DateAsString | null;
    failed: boolean;
    error: string | null;
}

export interface PlatformDto {
    name: string;
    displayName: string;
}

export interface EnumsResponse {
    platforms: PlatformDto[];
    providers: string[];
    transactionTypes: string[];
    categories: string[];
    currencies: string[];
}

export interface RecordingSummary {
    name: string;
    programme: string;
    status: RecordingStatus;
    sizeBytes: number;
    transcribed: boolean;
    transcriptCheckedAt: string | null;
    editList: EditList | null;
    failure: EpisodeFailure | null;
    episodeBytes: number | null;
    cuts: Cut[];
    timeline: TimelineEntry[];
    audible: KeptSpan[];
    format: AudioFormat | null;
    drops: number;
    transcriptState: TranscriptState;
    cutState: CutState;
}

export interface TranscriptLine {
    start: number;
    end: number;
    text: string;
    words: TranscriptWord[];
    split: Cut | null;
    music: boolean;
}

export interface CutReview {
    removed: number[];
}

export interface JingleReview {
    saved: boolean;
}

export interface ManualCut {
    start: number;
    end: number;
}

export interface CutContext {
    before: TranscriptLine | null;
    after: TranscriptLine | null;
}

export interface RadioProgrammeDto {
    id: number;
    name: string;
    description: string | null;
    recordEvery: boolean;
    slots: AirSlot[];
    recordings: number;
}

export interface ProgrammeChoice {
    recordEvery: boolean;
}

export interface RadioStation {
    onAir: StationAiring | null;
    next: StationAiring | null;
    capture: Capture | null;
    freeBytes: number | null;
    hours: number | null;
    recorder: RecorderState;
    last: LastRecording | null;
    format: AudioFormat | null;
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

export interface DiversificationIndustryDto extends Serializable {
    industry: string;
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

export interface FundWeightDto {
    isin: string;
    name: string;
    weight: number;
}

export interface RecordingStatus {
    state: RecordingState;
    startedAt: number;
    seconds: number;
    parts: RecordingPart[];
    programmes: string[];
    lostSeconds: number;
}

export interface EditList {
    keep: KeptSpan[];
    durationSeconds: number;
    parts: RecordingPart[];
    boundaries: Boundary[];
    hosts: string[];
    outline: OutlineSummary;
    chapters: EpisodeChapter[];
    reviewed: boolean;
    renderedAt: string;
    topics: EpisodeChapter[];
    music: KeptSpan[];
    manual: ManualCut[];
}

export interface EpisodeFailure {
    reason: string;
    failedAt: string;
}

export interface Cut {
    kind: CutKind;
    title: string;
    start: number;
    end: number;
    methods: BoundaryMethod[];
    review: boolean;
    removed: boolean;
    jingle: CutJingle | null;
}

export interface TimelineEntry {
    start: number;
    end: number;
    title: string;
    cut: boolean;
}

export interface KeptSpan {
    start: number;
    end: number;
}

export interface AudioFormat {
    codec: string;
    bitRate: number;
}

export interface TranscriptWord {
    start: number;
    end: number;
    word: string;
}

export interface AirSlot {
    days: DayOfWeek[];
    start: DateAsString;
    end: DateAsString;
}

export interface StationAiring {
    name: string;
    start: number;
    end: number;
    recorded: boolean;
}

export interface Capture {
    name: string;
    programmes: string[];
    start: number;
    end: number;
}

export interface LastRecording {
    programme: string;
    end: number;
}

export interface LargestPositionDto extends Serializable {
    name: string;
    percentage: number;
}

export interface RecordingPart {
    file: string;
    offsetSeconds: number;
    sha256: string | null;
}

export interface Boundary {
    boundary: string;
    seconds: number;
    method: string;
    review: boolean;
    jingle: JingleMatch | null;
}

export interface OutlineSummary {
    model: string | null;
    reads: number;
    matched: number;
    voters: number;
}

export interface EpisodeChapter {
    start: number;
    title: string;
}

export interface CutJingle {
    r: number;
    saved: boolean;
}

export interface JingleMatch {
    start: number;
    r: number;
    saved: string | null;
}

type DateAsString = string;

export enum CollectionStatus {
    OK = "OK",
    DISABLED = "DISABLED",
    BREAKER_OPEN = "BREAKER_OPEN",
    RUNNING = "RUNNING",
    OVERDUE = "OVERDUE",
    PARTIAL_FAILURE = "PARTIAL_FAILURE",
}

export enum Currency {
    EUR = "EUR",
    USD = "USD",
    GBP = "GBP",
    CHF = "CHF",
    JPY = "JPY",
    CAD = "CAD",
    AUD = "AUD",
    SEK = "SEK",
    NOK = "NOK",
    DKK = "DKK",
    HKD = "HKD",
    SGD = "SGD",
}

export enum Platform {
    AVIVA = "AVIVA",
    COINBASE = "COINBASE",
    IBKR = "IBKR",
    LHV = "LHV",
    LIGHTYEAR = "LIGHTYEAR",
    LIGHTYEAR_BUSINESS = "LIGHTYEAR_BUSINESS",
    REVOLUT_X = "REVOLUT_X",
    SWEDBANK = "SWEDBANK",
    TRADING212 = "TRADING212",
    TREZOR = "TREZOR",
    TULEVA = "TULEVA",
    UNKNOWN = "UNKNOWN",
}

export enum ProviderName {
    BINANCE = "BINANCE",
    FT = "FT",
    LIGHTYEAR = "LIGHTYEAR",
    MANUAL = "MANUAL",
    SYNTHETIC = "SYNTHETIC",
    TRADING212 = "TRADING212",
    TULEVA = "TULEVA",
}

export enum TransactionType {
    BUY = "BUY",
    SELL = "SELL",
}

export enum BenchmarkIndex {
    SP500 = "SP500",
    VWCE = "VWCE",
}

export enum TimeRange {
    ONE_DAY = "1D",
    TWO_DAYS = "2D",
    THREE_DAYS = "3D",
    FOUR_DAYS = "4D",
    FIVE_DAYS = "5D",
    SIX_DAYS = "6D",
    ONE_WEEK = "1W",
    ONE_MONTH = "1M",
    TWO_MONTHS = "2M",
    THREE_MONTHS = "3M",
    FOUR_MONTHS = "4M",
    FIVE_MONTHS = "5M",
    SIX_MONTHS = "6M",
    YTD = "YTD",
    ONE_YEAR = "1Y",
    TWO_YEARS = "2Y",
    THREE_YEARS = "3Y",
    FOUR_YEARS = "4Y",
    FIVE_YEARS = "5Y",
    SIX_YEARS = "6Y",
    MAX = "MAX",
}

export enum LiveUpdate {
    PRICES = "PRICES",
    SUMMARY = "SUMMARY",
}

export enum TranscriptState {
    NONE = "NONE",
    RECORDING = "RECORDING",
    UNCHECKED = "UNCHECKED",
    CHECKED = "CHECKED",
}

export enum CutState {
    NONE = "NONE",
    FAILED = "FAILED",
    SUGGESTED = "SUGGESTED",
    REVIEWED = "REVIEWED",
}

export enum RecorderState {
    WAITING = "WAITING",
    RECORDING = "RECORDING",
    OFF = "OFF",
}

export enum RecordingState {
    RECORDING = "recording",
    CAPTURED = "captured",
    PARTIAL = "partial",
    FAILED = "failed",
}

export enum CutKind {
    BEFORE = "BEFORE",
    BREAK = "BREAK",
    AFTER = "AFTER",
    MANUAL = "MANUAL",
}

export enum BoundaryMethod {
    DIGITAL_SILENCE = "DIGITAL_SILENCE",
    STATION_JINGLE = "STATION_JINGLE",
    LEVEL_DIP = "LEVEL_DIP",
    TRANSCRIPT_ONLY = "TRANSCRIPT_ONLY",
    MANUAL = "MANUAL",
}

export enum DayOfWeek {
    MONDAY = "MONDAY",
    TUESDAY = "TUESDAY",
    WEDNESDAY = "WEDNESDAY",
    THURSDAY = "THURSDAY",
    FRIDAY = "FRIDAY",
    SATURDAY = "SATURDAY",
    SUNDAY = "SUNDAY",
}
