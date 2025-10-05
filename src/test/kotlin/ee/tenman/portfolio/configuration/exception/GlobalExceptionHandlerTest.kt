package ee.tenman.portfolio.configuration.exception

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.GlobalExceptionHandler
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

@ExtendWith(MockitoExtension::class)
class GlobalExceptionHandlerTest {
  @InjectMocks
  private lateinit var globalExceptionHandler: GlobalExceptionHandler

  @Mock
  private lateinit var bindingResult: BindingResult

  @Mock
  private lateinit var methodParameter: MethodParameter

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
    val mockPath1: Path = mock()
    whenever(mockPath1.toString()).thenReturn("field1")
    val mockViolation1: ConstraintViolation<*> = mock()
    whenever(mockViolation1.propertyPath).thenReturn(mockPath1)
    whenever(mockViolation1.message).thenReturn("Field 1 is invalid")

    val mockPath2: Path = mock()
    whenever(mockPath2.toString()).thenReturn("field2")
    val mockViolation2: ConstraintViolation<*> = mock()
    whenever(mockViolation2.propertyPath).thenReturn(mockPath2)
    whenever(mockViolation2.message).thenReturn("Field 2 is invalid")

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
    whenever(bindingResult.fieldErrors).thenReturn(fieldErrors)

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
    whenever(bindingResult.fieldErrors).thenReturn(fieldErrors)

    val methodArgumentNotValidException = MethodArgumentNotValidException(methodParameter, bindingResult)
    val response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException)

    expect(response.statusCode).toEqual(HttpStatus.BAD_REQUEST)
    expect(response.body?.message).toEqual("Validation error")
    expect(response.body?.debugMessage).toEqual("One or more fields have an error")
    expect(response.body?.validationErrors?.get("field1")).toEqual("Field 1 is invalid")
    expect(response.body?.validationErrors?.get("field2")).toEqual("Field 2 is invalid")
  }
}
