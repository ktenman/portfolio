package ee.tenman.portfolio.tuleva

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.net.URI

@FeignClient(
  name = "tulevaReportClient",
  url = "\${tuleva.web-url}",
)
interface TulevaReportClient {
  @GetMapping("/wp-json/wp/v2/media?search=III-Samba-Pensionifondi-investeeringute&per_page=$MEDIA_PAGE_SIZE&_fields=source_url")
  fun listMedia(
    @RequestParam page: Int,
  ): ResponseEntity<List<TulevaMedia>>

  @GetMapping
  fun download(uri: URI): ByteArray
}

const val MEDIA_PAGE_SIZE = 100

const val TOTAL_PAGES_HEADER = "X-WP-TotalPages"

data class TulevaMedia(
  @JsonProperty("source_url")
  val sourceUrl: String,
)
