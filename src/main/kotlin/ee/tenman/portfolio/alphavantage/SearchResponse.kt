package ee.tenman.portfolio.alphavantage

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResponse(
  @JsonProperty("bestMatches")
  val bestMatches: List<SearchData>? = null,
  @JsonProperty("Information")
  val information: String? = null,
  @JsonProperty("Error Message")
  val errorMessage: String? = null,
) {
  data class SearchData(
    @JsonProperty("1. symbol")
    val symbol: String,
    @JsonProperty("2. name")
    val name: String,
    @JsonProperty("3. type")
    val type: String,
    @JsonProperty("4. region")
    val region: String,
    @JsonProperty("5. marketOpen")
    val marketOpen: String,
    @JsonProperty("6. marketClose")
    val marketClose: String,
    @JsonProperty("7. timezone")
    val timezone: String,
    @JsonProperty("8. currency")
    val currency: String,
    @JsonProperty("9. matchScore")
    val matchScore: String,
  ) : Comparable<SearchData> {
    override fun compareTo(other: SearchData): Int = this.symbol.compareTo(other.symbol)
  }
}
