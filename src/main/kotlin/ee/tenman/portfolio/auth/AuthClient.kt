package ee.tenman.portfolio.auth

import ee.tenman.portfolio.auth.model.AuthResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = AuthClient.CLIENT_NAME, url = "\${auth.url}")
fun interface AuthClient {
  @GetMapping("/user")
  fun getUser(@RequestParam sessionId: String): ResponseEntity<AuthResponse>

  companion object {
    const val CLIENT_NAME = "authClient"
  }
}
