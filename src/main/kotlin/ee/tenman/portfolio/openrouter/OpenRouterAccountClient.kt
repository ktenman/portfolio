package ee.tenman.portfolio.openrouter

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
  name = "openrouter-account",
  url = "\${openrouter.url:https://openrouter.ai/api/v1}",
)
interface OpenRouterAccountClient {
  @GetMapping("/credits")
  fun credits(
    @RequestHeader("Authorization") authorization: String,
  ): OpenRouterCredits

  @GetMapping("/key")
  fun key(
    @RequestHeader("Authorization") authorization: String,
  ): OpenRouterKey
}
