package ee.tenman.portfolio.auto24

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
  name = "auto24ProxyClient",
  url = "\${cloudflare-bypass-proxy.url:http://localhost:3000}",
)
interface Auto24ProxyClient {
  @PostMapping("/auto24/captcha")
  fun getCaptcha(
    @RequestBody request: CaptchaRequest,
  ): CaptchaResponse

  @PostMapping("/auto24/submit")
  fun submitCaptcha(
    @RequestBody request: SubmitRequest,
  ): SubmitResponse
}
