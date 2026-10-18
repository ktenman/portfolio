package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.radio.CutContext
import ee.tenman.portfolio.service.radio.CutReview
import ee.tenman.portfolio.service.radio.EditList
import ee.tenman.portfolio.service.radio.JingleReview
import ee.tenman.portfolio.service.radio.ManualCut
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.RadioArchiveService
import ee.tenman.portfolio.service.radio.RadioProgrammeDto
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.RadioStation
import ee.tenman.portfolio.service.radio.RecordingSummary
import ee.tenman.portfolio.service.radio.TranscriptLine
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.text.Charsets.UTF_8

@RestController
@RequestMapping("/api/radio")
class RadioController(
  private val radioArchiveService: RadioArchiveService,
  private val radioScheduleService: RadioScheduleService,
) {
  @GetMapping
  fun getRecordings(): List<RecordingSummary> = radioArchiveService.recordings()

  @GetMapping("/programmes")
  fun getProgrammes(): List<RadioProgrammeDto> = radioScheduleService.programmes()

  @GetMapping("/station")
  fun getStation(): RadioStation = radioScheduleService.station()

  @PutMapping("/programmes/{id}")
  fun putProgramme(
    @PathVariable id: Long,
    @RequestBody choice: ProgrammeChoice,
  ): ResponseEntity<Void> = done(radioScheduleService.choose(id, choice))

  @GetMapping("/{name}/transcript")
  fun getTranscript(
    @PathVariable name: String,
  ): ResponseEntity<List<TranscriptLine>> = ResponseEntity.ofNullable(radioArchiveService.transcript(name))

  @GetMapping("/{name}/transcript.txt", produces = ["text/plain;charset=UTF-8"])
  fun getText(
    @PathVariable name: String,
  ): ResponseEntity<String> = ResponseEntity.ofNullable(radioArchiveService.text(name))

  @GetMapping("/{name}/transcript.srt", produces = ["application/x-subrip;charset=UTF-8"])
  fun getSubtitles(
    @PathVariable name: String,
  ): ResponseEntity<String> = ResponseEntity.ofNullable(radioArchiveService.subtitles(name))

  @GetMapping("/{name}/cuts")
  fun getCuts(
    @PathVariable name: String,
  ): ResponseEntity<List<CutContext>> = ResponseEntity.ofNullable(radioArchiveService.contexts(name))

  @GetMapping("/{name}/{file}")
  fun getFile(
    @PathVariable name: String,
    @PathVariable file: String,
  ): ResponseEntity<Resource> = radioArchiveService.file(name, file)?.let(::media) ?: ResponseEntity.notFound().build()

  @PutMapping("/{name}/cuts")
  fun putCuts(
    @PathVariable name: String,
    @RequestBody review: CutReview,
  ): ResponseEntity<EditList> = ResponseEntity.ofNullable(radioArchiveService.review(name, review))

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

  @PutMapping("/{name}/cuts/{index}/jingle")
  fun putJingle(
    @PathVariable name: String,
    @PathVariable index: Int,
    @RequestBody review: JingleReview,
  ): ResponseEntity<Void> = done(radioArchiveService.jingle(name, index, review))

  @PutMapping("/{name}/transcript/checked")
  fun putTranscriptChecked(
    @PathVariable name: String,
  ): ResponseEntity<Void> = done(radioArchiveService.check(name))

  private fun done(found: Boolean): ResponseEntity<Void> =
    if (found) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

  private fun media(resource: Resource): ResponseEntity<Resource> =
    ResponseEntity
      .ok()
      .contentType(type(resource))
      .lastModified(resource.lastModified())
      .cacheControl(CacheControl.noCache())
      .body(resource)

  private fun type(resource: Resource): MediaType {
    val type = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM)
    return if (type.type == "text") MediaType(type, UTF_8) else type
  }
}
