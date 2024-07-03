package ee.tenman.portfolio.alphavantage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.TreeMap;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlphaVantageResponse {
  @JsonProperty("Monthly Time Series")
  private TreeMap<String, AlphaVantageDayData> monthlyTimeSeries;

  @JsonProperty("Information")
  private String information;

  @JsonProperty("Error Message")
  private String errorMessage;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AlphaVantageDayData {
    @JsonProperty("4. close")
    private BigDecimal close;
  }

}
