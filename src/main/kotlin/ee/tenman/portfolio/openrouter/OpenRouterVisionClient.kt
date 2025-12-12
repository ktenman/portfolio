package ee.tenman.portfolio.openrouter

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
  name = "openrouter-vision",
  url = "\${openrouter.url:https://openrouter.ai/api/v1}",
)
interface OpenRouterVisionClient {
  @PostMapping("/chat/completions")
  fun chatCompletion(
    @RequestHeader("Authorization") authorization: String,
    @RequestBody request: OpenRouterVisionRequest,
  ): OpenRouterResponse
}
