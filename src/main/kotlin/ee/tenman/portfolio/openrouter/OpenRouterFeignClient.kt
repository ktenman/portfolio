package ee.tenman.portfolio.openrouter

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
  name = "openrouter",
  url = "\${openrouter.url:https://openrouter.ai/api/v1}",
  configuration = [OpenRouterFeignConfiguration::class],
)
interface OpenRouterFeignClient {
  @PostMapping("/chat/completions")
  fun chatCompletion(
    @RequestHeader("Authorization") authorization: String,
    @RequestBody request: OpenRouterRequest,
  ): OpenRouterResponse
}
