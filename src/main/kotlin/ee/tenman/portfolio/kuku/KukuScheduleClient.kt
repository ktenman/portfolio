package ee.tenman.portfolio.kuku

import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.service.radio.ScheduledAiring
import ee.tenman.portfolio.service.radio.ScheduledShow
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
) {
  fun scheduled(): ScheduledAiring? =
    show?.let {
      ScheduledAiring(id, ScheduledShow(it.id, it.name, it.slug, it.description), title, start.toInstant(), end.toInstant(), originalId)
    }
}

data class KukuShow(
  val id: Long,
  val name: String,
  val slug: String,
  @JsonProperty("description_short")
  val description: String? = null,
)
