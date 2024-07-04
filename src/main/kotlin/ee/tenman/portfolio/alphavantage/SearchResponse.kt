package ee.tenman.portfolio.alphavantage

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResponse(
  @JsonProperty("bestMatches")
  val bestMatches: List<SearchData>? = null,

  @JsonProperty("Information")
  val information: String? = null,

  @JsonProperty("Error Message")
  val errorMessage: String? = null
) {
  data class SearchData(
    @JsonProperty("1. symbol")
    val symbol: String,

    @JsonProperty("8. currency")
    val currency: String
  ) : Comparable<SearchData> {
    override fun compareTo(other: SearchData): Int = this.symbol.compareTo(other.symbol)
  }
}
