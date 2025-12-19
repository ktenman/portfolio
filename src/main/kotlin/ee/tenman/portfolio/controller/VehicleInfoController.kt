package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.VehicleInfoResponse
import ee.tenman.portfolio.service.VehicleInfoService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/vehicle")
class VehicleInfoController(
  private val vehicleInfoService: VehicleInfoService,
) {
  @GetMapping("/info")
  fun getVehicleInfo(
    @RequestParam @NotBlank @Size(max = 20) plateNumber: String,
  ): VehicleInfoResponse = vehicleInfoService.getVehicleInfo(plateNumber)
}
