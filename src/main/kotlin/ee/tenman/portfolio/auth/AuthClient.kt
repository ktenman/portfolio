package ee.tenman.portfolio.auth

import ee.tenman.portfolio.auth.model.AuthResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(name = AuthClient.CLIENT_NAME, url = "\${auth.url}")
interface AuthClient {
  @GetMapping(
    "/user-by-session",
    consumes = [MediaType.APPLICATION_JSON_VALUE],
    produces = [MediaType.APPLICATION_JSON_VALUE]
  )
  fun getUser(
    @RequestParam sessionId: String,
    @RequestHeader("Accept") accept: String = MediaType.APPLICATION_JSON_VALUE,
    @RequestHeader("Content-Type") contentType: String = MediaType.APPLICATION_JSON_VALUE
  ): AuthResponse

  companion object {
    const val CLIENT_NAME = "authClient"
  }
}
