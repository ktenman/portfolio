package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.radio.CutReview
import ee.tenman.portfolio.service.radio.EditList
import ee.tenman.portfolio.service.radio.RadioArchiveService
import ee.tenman.portfolio.service.radio.RecordingSummary
import ee.tenman.portfolio.service.radio.TranscriptLine
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.text.Charsets.UTF_8

@RestController
@RequestMapping("/api/radio")
class RadioController(
  private val radioArchiveService: RadioArchiveService,
) {
  @GetMapping
  fun getRecordings(): List<RecordingSummary> = radioArchiveService.recordings()

  @GetMapping("/{name}/transcript")
  fun getTranscript(
    @PathVariable name: String,
  ): ResponseEntity<List<TranscriptLine>> = ResponseEntity.ofNullable(radioArchiveService.transcript(name))

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

  @PutMapping("/{name}/transcript/checked")
  fun putTranscriptChecked(
    @PathVariable name: String,
  ): ResponseEntity<Void> = if (radioArchiveService.check(name)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

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
