package ee.tenman.portfolio.imdb

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
  name = "imdbSuggestionClient",
  url = "\${imdb.suggestion-url}",
)
interface ImdbSuggestionClient {
  @GetMapping("/suggestion/x/{query}.json")
  fun suggest(
    @PathVariable query: String,
  ): ImdbSuggestions
}

data class ImdbSuggestions(
  val d: List<ImdbSuggestion> = emptyList(),
)

data class ImdbSuggestion(
  val id: String,
  @JsonProperty("l") val title: String? = null,
  val qid: String? = null,
  @JsonProperty("s") val stars: String? = null,
  @JsonProperty("y") val year: Int? = null,
)
