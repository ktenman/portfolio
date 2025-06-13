package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.EnumService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/enums")
class EnumController(
    private val enumService: EnumService
) {

    @GetMapping
    fun getAllEnums(): Map<String, List<String>> = enumService.getAllEnums()
}