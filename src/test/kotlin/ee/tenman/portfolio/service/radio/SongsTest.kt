package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class SongsTest {
  private val show = listOf(0.0 to 400.0)

  private fun talk(
    from: Double,
    to: Double,
  ) = heard(from, to, 0.98, 0.0)

  private fun song(
    from: Double,
    to: Double,
  ) = heard(from, to, 0.0, 0.9)

  @Test
  fun `should cut a song between two stretches of talk`() {
    expect(songs(talk(0.0, 100.0) + song(100.0, 300.0) + talk(300.0, 400.0), show, emptyList())).toEqual(listOf(100.0 to 299.5))
  }

  @Test
  fun `should keep talk over a music bed`() {
    expect(songs(heard(0.0, 400.0, 0.62, 0.91), show, emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should keep music where the classifier still hears some speech`() {
    expect(songs(heard(0.0, 400.0, 0.35, 0.8), show, emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should keep music shorter than ten seconds`() {
    expect(songs(talk(0.0, 100.0) + song(100.0, 109.0) + talk(109.0, 400.0), show, emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should cut a song through a spoken line of three seconds inside it`() {
    val frames = talk(0.0, 100.0) + song(100.0, 200.0) + talk(200.0, 203.0) + song(203.0, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, emptyList())).toEqual(listOf(100.0 to 299.5))
  }

  @Test
  fun `should keep a spoken line of more than three seconds between two songs`() {
    val frames = talk(0.0, 100.0) + song(100.0, 200.0) + talk(200.0, 203.5) + song(203.5, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, emptyList())).toEqual(listOf(100.0 to 199.5, 203.5 to 299.5))
  }

  @Test
  fun `should cut a song through a stretch where nobody talks`() {
    val frames = talk(0.0, 100.0) + song(100.0, 200.0) + heard(200.0, 210.0, 0.01, 0.2) + song(210.0, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, emptyList())).toEqual(listOf(100.0 to 299.5))
  }

  @Test
  fun `should keep a quiet stretch longer than fifteen seconds between two songs`() {
    val frames = talk(0.0, 100.0) + song(100.0, 200.0) + heard(200.0, 216.0, 0.01, 0.2) + song(216.0, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, emptyList())).toEqual(listOf(100.0 to 199.5, 216.0 to 299.5))
  }

  @Test
  fun `should end a song where its music last held for two seconds`() {
    val frames = talk(0.0, 100.0) + song(100.0, 300.0) + talk(300.0, 301.0) + song(301.0, 302.5) + talk(302.5, 400.0)
    expect(songs(frames, show, emptyList())).toEqual(listOf(100.0 to 299.5))
  }

  @Test
  fun `should cut only the part of a song inside the show`() {
    val frames = talk(0.0, 600.0) + song(600.0, 700.0) + talk(700.0, 800.0)
    expect(songs(frames, listOf(0.0 to 665.0), emptyList())).toEqual(listOf(600.0 to 665.0))
  }

  @Test
  fun `should leave a sliver of a song shorter than three seconds in the show`() {
    val frames = talk(0.0, 640.0) + song(640.0, 770.0) + talk(770.0, 900.0)
    expect(songs(frames, listOf(767.5 to 900.0), emptyList())).toEqual(emptyList())
  }

  @Test
  fun `should start a cut after the word it would split`() {
    val frames = talk(0.0, 100.0) + song(100.0, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, listOf(TranscriptWord(99.8, 100.4, " muusikat")))).toEqual(listOf(100.4 to 299.5))
  }

  @Test
  fun `should end a cut before the word it would split`() {
    val frames = talk(0.0, 100.0) + song(100.0, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, listOf(TranscriptWord(299.3, 299.9, " Tere")))).toEqual(listOf(100.0 to 299.3))
  }

  @Test
  fun `should cut through a word the transcript stretched over the music`() {
    val frames = talk(0.0, 100.0) + song(100.0, 300.0) + talk(300.0, 400.0)
    expect(songs(frames, show, listOf(TranscriptWord(99.6, 124.2, " to")))).toEqual(listOf(100.0 to 299.5))
  }

  @Test
  fun `should join frames whose times were rounded to milliseconds`() {
    val frames = (0..30).map { SoundFrame(BigDecimal(1010.4875 + it * 0.5).setScale(3, RoundingMode.HALF_EVEN).toDouble(), 0.0, 0.9) }
    expect(songs(frames, listOf(0.0 to 2000.0), emptyList())).toEqual(listOf(1010.487 to 1025.487))
  }

  @Test
  fun `should cut nothing without sound frames`() {
    expect(songs(emptyList(), show, emptyList())).toEqual(emptyList())
  }
}
