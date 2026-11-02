package ee.tenman.portfolio.aripaev

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping

@FeignClient(
  name = "aripaevScheduleClient",
  url = "\${aripaev.url}",
)
interface AripaevScheduleClient {
  @GetMapping("/raadio/saatekava", headers = ["RSC=1"])
  fun getSchedule(): String
}
