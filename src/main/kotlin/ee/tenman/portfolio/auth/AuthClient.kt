package ee.tenman.portfolio.auth

import ee.tenman.portfolio.auth.model.AuthResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = AuthClient.CLIENT_NAME, url = "\${auth.url}")
interface AuthClient {
  @GetMapping(
    "/user-by-session",
    produces = [MediaType.APPLICATION_JSON_VALUE]
  )
  fun getUser(
    @RequestParam sessionId: String
  ): AuthResponse

  companion object {
    const val CLIENT_NAME = "authClient"
  }
}
