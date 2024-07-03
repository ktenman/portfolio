package ee.tenman.portfolio.alphavantage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponse {
  @JsonProperty("bestMatches")
  private List<SearchData> bestMatches;

  @JsonProperty("Information")
  private String information;

  @JsonProperty("Error Message")
  private String errorMessage;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SearchData implements Comparable<SearchData> {
    @JsonProperty("1. symbol")
    private String symbol;

    @JsonProperty("8. currency")
    private String currency;

    @Override
    public int compareTo(final SearchData other) {
      return this.symbol.compareTo(other.getSymbol());
    }
  }
}
