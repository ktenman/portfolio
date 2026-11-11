package ee.tenman.portfolio.skyplus

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import java.time.LocalTime

@FeignClient(
  name = "skyplusScheduleClient",
  url = "\${skyplus.url}",
)
interface SkyplusScheduleClient {
  @GetMapping("/api/radio-stations/1/schedule")
  fun getSchedule(): SkyplusSchedule
}

data class SkyplusSchedule(
  val data: List<SkyplusRow>,
)

data class SkyplusRow(
  val id: Long,
  val name: String,
  val days: List<Int>,
  @JsonProperty("start_time")
  val startTime: LocalTime,
  @JsonProperty("end_time")
  val endTime: LocalTime,
)
