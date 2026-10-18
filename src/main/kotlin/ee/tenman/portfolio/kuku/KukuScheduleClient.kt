package ee.tenman.portfolio.kuku

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.OffsetDateTime

@FeignClient(
  name = "kukuScheduleClient",
  url = "\${kuku.url}",
)
interface KukuScheduleClient {
  @GetMapping("/api/schedule")
  fun getSchedule(
    @RequestParam domain: String,
    @RequestParam start: String,
    @RequestParam end: String,
  ): List<KukuDay>
}

data class KukuDay(
  val episodes: List<KukuEpisode>,
)

data class KukuEpisode(
  val id: Long,
  val start: OffsetDateTime,
  val end: OffsetDateTime,
  val title: String,
  @JsonProperty("original_id")
  val originalId: Long? = null,
  val show: KukuShow? = null,
)

data class KukuShow(
  val id: Long,
  val name: String,
  val slug: String,
  @JsonProperty("description_short")
  val description: String? = null,
)
