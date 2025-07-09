package ee.tenman.portfolio.controller

import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {
  @GetMapping("/")
    fun home(response: HttpServletResponse) {
        response.sendRedirect("/swagger-ui.html")
    }
}
