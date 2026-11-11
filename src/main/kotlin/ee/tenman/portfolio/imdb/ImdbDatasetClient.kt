package ee.tenman.portfolio.imdb

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping

@FeignClient(
  name = "imdbDatasetClient",
  url = "\${imdb.datasets-url}",
)
interface ImdbDatasetClient {
  @GetMapping("/title.ratings.tsv.gz")
  fun ratings(): ByteArray
}
