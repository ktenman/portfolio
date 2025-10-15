package ee.tenman.portfolio.configuration

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.support.WebExchangeBindException

@ControllerAdvice
class GlobalExceptionHandler {
  private val log = LoggerFactory.getLogger(javaClass)
  @ExceptionHandler(WebExchangeBindException::class, MethodArgumentNotValidException::class)
  fun handleValidationExceptions(exception: Exception): ResponseEntity<ApiError> = handleValidationException(exception)

  @ExceptionHandler(Exception::class)
  fun handleAllExceptions(exception: Exception): ResponseEntity<ApiError> {
    log.error("Unhandled exception: ${exception.message}", exception)
    val apiError =
      ApiError(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        message = exception.localizedMessage,
        debugMessage = "An internal error occurred",
      )
    return ResponseEntity(apiError, apiError.status)
  }

  @ExceptionHandler(ConstraintViolationException::class)
  fun handleConstraintViolationException(exception: ConstraintViolationException): ResponseEntity<ApiError> {
    val errors =
      exception.constraintViolations.associate { violation ->
        violation.propertyPath.toString() to violation.message
      }
    val apiError =
      ApiError(
        status = HttpStatus.BAD_REQUEST,
        message = "Validation error",
        debugMessage = "Constraint violation occurred",
        validationErrors = errors,
      )
    return ResponseEntity(apiError, HttpStatus.BAD_REQUEST)
  }

  fun handleValidationException(exception: Exception): ResponseEntity<ApiError> {
    val errors = extractErrors(exception)
    val apiError =
      ApiError(
        status = HttpStatus.BAD_REQUEST,
        message = "Validation error",
        debugMessage = "One or more fields have an error",
        validationErrors = errors,
      )
    return ResponseEntity(apiError, HttpStatus.BAD_REQUEST)
  }

  private fun extractErrors(exception: Exception): Map<String, String> {
    val bindingResult: BindingResult? =
      when (exception) {
        is MethodArgumentNotValidException -> exception.bindingResult
        is WebExchangeBindException -> exception.bindingResult
        else -> null
      }

    return bindingResult?.fieldErrors?.associate { fieldError ->
      fieldError.field to (fieldError.defaultMessage ?: "Invalid value")
    } ?: emptyMap()
  }

  data class ApiError(
    val status: HttpStatus,
    val message: String,
    val debugMessage: String,
    val validationErrors: Map<String, String> = emptyMap(),
  )
}
