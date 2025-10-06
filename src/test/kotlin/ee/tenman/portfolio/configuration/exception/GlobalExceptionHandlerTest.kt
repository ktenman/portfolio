package ee.tenman.portfolio.configuration.exception

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class GlobalExceptionHandlerTest {
  private val globalExceptionHandler = GlobalExceptionHandler()
  private val bindingResult: BindingResult = mockk()
  private val methodParameter: MethodParameter = mockk(relaxed = true)

  @Test
  fun `should return internal server error when handling general exceptions`() {
    val exception = Exception("Test exception")

    val response = globalExceptionHandler.handleAllExceptions(exception)

    expect(response.statusCode).toEqual(HttpStatus.INTERNAL_SERVER_ERROR)
    expect(response.body?.message).toEqual("Test exception")
    expect(response.body?.debugMessage).toEqual("An internal error occurred")
  }

  @Test
  fun `should return bad request with validation errors when handling ConstraintViolationException`() {
    val mockPath1: Path = mockk()
    every { mockPath1.toString() } returns "field1"
    val mockViolation1: ConstraintViolation<*> = mockk()
    every { mockViolation1.propertyPath } returns mockPath1
    every { mockViolation1.message } returns "Field 1 is invalid"

    val mockPath2: Path = mockk()
    every { mockPath2.toString() } returns "field2"
    val mockViolation2: ConstraintViolation<*> = mockk()
    every { mockViolation2.propertyPath } returns mockPath2
    every { mockViolation2.message } returns "Field 2 is invalid"

    val constraintViolations = setOf(mockViolation1, mockViolation2)
    val exception = ConstraintViolationException(constraintViolations)

    val response = globalExceptionHandler.handleConstraintViolationException(exception)

    expect(response.statusCode).toEqual(HttpStatus.BAD_REQUEST)
    expect(response.body?.message).toEqual("Validation error")
    expect(response.body?.debugMessage).toEqual("Constraint violation occurred")
    expect(response.body?.validationErrors?.get("field1")).toEqual("Field 1 is invalid")
    expect(response.body?.validationErrors?.get("field2")).toEqual("Field 2 is invalid")
  }

  @Test
  fun `should return bad request with field errors when handling MethodArgumentNotValidException`() {
    val fieldErrors =
      listOf(
        FieldError("objectName", "field1", "Field 1 is invalid"),
        FieldError("objectName", "field2", "Field 2 is invalid"),
      )
    every { bindingResult.fieldErrors } returns fieldErrors

    val methodArgumentNotValidException = MethodArgumentNotValidException(methodParameter, bindingResult)
    val response = globalExceptionHandler.handleValidationExceptions(methodArgumentNotValidException)

    expect(response.statusCode).toEqual(HttpStatus.BAD_REQUEST)
    expect(response.body?.message).toEqual("Validation error")
    expect(response.body?.debugMessage).toEqual("One or more fields have an error")
    expect(response.body?.validationErrors?.get("field1")).toEqual("Field 1 is invalid")
    expect(response.body?.validationErrors?.get("field2")).toEqual("Field 2 is invalid")
  }

  @Test
  fun `should extract field errors when handling validation exceptions`() {
    val fieldErrors =
      listOf(
        FieldError("objectName", "field1", "Field 1 is invalid"),
        FieldError("objectName", "field2", "Field 2 is invalid"),
      )
    every { bindingResult.fieldErrors } returns fieldErrors

    val methodArgumentNotValidException = MethodArgumentNotValidException(methodParameter, bindingResult)
    val response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException)

    expect(response.statusCode).toEqual(HttpStatus.BAD_REQUEST)
    expect(response.body?.message).toEqual("Validation error")
    expect(response.body?.debugMessage).toEqual("One or more fields have an error")
    expect(response.body?.validationErrors?.get("field1")).toEqual("Field 1 is invalid")
    expect(response.body?.validationErrors?.get("field2")).toEqual("Field 2 is invalid")
  }
}
