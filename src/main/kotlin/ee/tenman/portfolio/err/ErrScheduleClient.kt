package ee.tenman.portfolio.err

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
  name = "errScheduleClient",
  url = "\${err.url}",
)
interface ErrScheduleClient {
  @GetMapping("/api/v1/getSchedule/raadio2/{midnight}")
  fun getSchedule(
    @PathVariable midnight: Long,
  ): ErrSchedule
}

data class ErrSchedule(
  val data: ErrScheduleData,
)

data class ErrScheduleData(
  val channels: List<ErrChannel>,
)

data class ErrChannel(
  val items: List<ErrScheduleItem>,
)

data class ErrScheduleItem(
  val radiomanId: Long,
  val seriesName: String,
  val heading: String? = null,
  val startTime: Long,
  val endTime: Long,
  val isReplay: Boolean = false,
  val originalTime: Long? = null,
)
