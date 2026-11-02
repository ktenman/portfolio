package ee.tenman.portfolio.controller

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.exception.RecordingInProgressException
import ee.tenman.portfolio.service.radio.AiringChoice
import ee.tenman.portfolio.service.radio.CutChoice
import ee.tenman.portfolio.service.radio.CutContext
import ee.tenman.portfolio.service.radio.CutReview
import ee.tenman.portfolio.service.radio.JingleReview
import ee.tenman.portfolio.service.radio.ManualCut
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.RadioArchiveService
import ee.tenman.portfolio.service.radio.RadioDay
import ee.tenman.portfolio.service.radio.RadioDayService
import ee.tenman.portfolio.service.radio.RadioLiveService
import ee.tenman.portfolio.service.radio.RadioPositionDto
import ee.tenman.portfolio.service.radio.RadioPositionService
import ee.tenman.portfolio.service.radio.RadioProgrammeDto
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.RadioSearch
import ee.tenman.portfolio.service.radio.RadioSearchService
import ee.tenman.portfolio.service.radio.RadioStation
import ee.tenman.portfolio.service.radio.RecordingSummary
import ee.tenman.portfolio.service.radio.TranscriptSection
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.Resource
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import kotlin.text.Charsets.UTF_8

@RestController
@RequestMapping("/api/radio")
class RadioController(
  private val radioArchiveService: RadioArchiveService,
  private val radioScheduleService: RadioScheduleService,
  private val radioLiveService: RadioLiveService,
  private val radioDayService: RadioDayService,
  private val radioPositionService: RadioPositionService,
  private val radioSearchService: RadioSearchService,
) {
  @GetMapping
  fun getRecordings(): List<RecordingSummary> = radioArchiveService.recordings()

  @GetMapping("/programmes")
  fun getProgrammes(): List<RadioProgrammeDto> = radioScheduleService.programmes()

  @GetMapping("/station")
  fun getStations(): List<RadioStation> = radioScheduleService.stations()

  @GetMapping("/live")
  fun getLive(
    @RequestParam(defaultValue = "KUKU") station: Station,
    response: HttpServletResponse,
  ) {
    response.contentType = "audio/mpeg"
    radioLiveService.stream(response.outputStream, station)
  }

  @GetMapping("/schedule")
  fun getSchedule(
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    @RequestParam(required = false) station: Station?,
  ): RadioDay = radioDayService.day(date, station)

  @GetMapping("/search")
  fun getSearch(
    @RequestParam q: String,
    @RequestParam(required = false) after: String?,
    @RequestParam(required = false) station: Station?,
  ): RadioSearch = radioSearchService.search(q, after, station)

  @PutMapping("/airings/{id}")
  fun putAiring(
    @PathVariable id: Long,
    @RequestBody choice: AiringChoice,
  ): ResponseEntity<Void> = done(radioScheduleService.record(id, choice))

  @PutMapping("/programmes/{id}")
  fun putProgramme(
    @PathVariable id: Long,
    @RequestBody choice: ProgrammeChoice,
  ): ResponseEntity<Void> = done(radioScheduleService.choose(id, choice))

  @GetMapping("/{name}/transcript")
  fun getTranscript(
    @PathVariable name: String,
  ): ResponseEntity<List<TranscriptSection>> = ResponseEntity.ofNullable(radioArchiveService.sections(name))

  @GetMapping("/{name}/cuts")
  fun getCuts(
    @PathVariable name: String,
  ): ResponseEntity<List<CutContext>> = ResponseEntity.ofNullable(radioArchiveService.contexts(name))

  @GetMapping("/{name}/position")
  fun getPosition(
    @PathVariable name: String,
    @RequestHeader("X-User-Id") listener: String,
  ): RadioPositionDto = radioPositionService.find(listener, name)

  @PutMapping("/{name}/position")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun putPosition(
    @PathVariable name: String,
    @RequestHeader("X-User-Id") listener: String,
    @RequestBody position: RadioPositionDto,
  ) = radioPositionService.save(listener, name, position)

  @GetMapping("/{name}/{file}")
  fun getFile(
    @PathVariable name: String,
    @PathVariable file: String,
  ): ResponseEntity<Resource> = attachment(radioArchiveService.file(name, file), radioArchiveService.downloadName(name, file))

  @PutMapping("/{name}/cuts")
  fun putCuts(
    @PathVariable name: String,
    @RequestBody review: CutReview,
  ): ResponseEntity<Void> = done(radioArchiveService.review(name, review))

  @PostMapping("/{name}/cuts/manual")
  fun postManualCut(
    @PathVariable name: String,
    @RequestBody cut: ManualCut,
  ): ResponseEntity<Void> = done(radioArchiveService.add(name, cut))

  @DeleteMapping("/{name}/cuts/{index}")
  fun deleteCut(
    @PathVariable name: String,
    @PathVariable index: Int,
  ): ResponseEntity<Void> = done(radioArchiveService.drop(name, index))

  @PostMapping("/{name}/cuts/find")
  fun findCuts(
    @PathVariable name: String,
    @RequestBody choice: CutChoice,
  ): ResponseEntity<Void> = done(radioArchiveService.find(name, choice))

  @PutMapping("/{name}/cuts/{index}/jingle")
  fun putJingle(
    @PathVariable name: String,
    @PathVariable index: Int,
    @RequestBody review: JingleReview,
  ): ResponseEntity<Void> = done(radioArchiveService.jingle(name, index, review))

  @DeleteMapping("/{name}")
  fun deleteRecording(
    @PathVariable name: String,
  ): ResponseEntity<Void> = done(radioArchiveService.delete(name))

  @ExceptionHandler(RecordingInProgressException::class)
  fun handleRecordingInProgress(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.CONFLICT).build()

  private fun done(found: Boolean): ResponseEntity<Void> =
    if (found) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

  private fun attachment(
    resource: Resource?,
    file: String?,
  ): ResponseEntity<Resource> {
    if (resource == null || file == null) return ResponseEntity.notFound().build()
    val disposition = ContentDisposition.attachment().filename(file, UTF_8).build()
    return media(resource).header(CONTENT_DISPOSITION, disposition.toString()).body(resource)
  }

  private fun media(resource: Resource): ResponseEntity.BodyBuilder =
    ResponseEntity
      .ok()
      .contentType(type(resource))
      .lastModified(resource.lastModified())
      .cacheControl(CacheControl.noCache())

  private fun type(resource: Resource): MediaType {
    val type = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM)
    return if (type.type == "text") MediaType(type, UTF_8) else type
  }
}
