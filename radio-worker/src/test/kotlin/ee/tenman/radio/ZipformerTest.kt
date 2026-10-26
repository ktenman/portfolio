package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualWithErrorTolerance
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class ZipformerTest {
  @Test
  fun `joins the pieces of a word into one word`() {
    expect(words(arrayOf("▁tere", "tule", "mast"), floatArrayOf(0.0f, 0.25f, 0.5f), 2.0).map { it.word }).toEqual(listOf(" teretulemast"))
  }

  @Test
  fun `ends a word where the next word starts`() {
    expect(words(arrayOf("▁tere", "▁hommikust"), floatArrayOf(0.0f, 0.25f), 2.0).first().end).toEqualWithErrorTolerance(1.85, 1e-9)
  }

  @Test
  fun `ends a word at most a tail after its last piece`() {
    expect(words(arrayOf("▁tere", "▁õhtust"), floatArrayOf(0.0f, 1.5f), 2.0).first().end).toEqualWithErrorTolerance(2.2, 1e-9)
  }

  @Test
  fun `moves a word back by the time the model takes to emit it`() {
    expect(words(arrayOf("▁tere"), floatArrayOf(1.0f), 2.0).first().start).toEqualWithErrorTolerance(2.6, 1e-9)
  }

  @Test
  fun `never moves a word before the start of the audio`() {
    expect(words(arrayOf("▁tere"), floatArrayOf(0.1f), 0.0).first().start).toEqual(0.0)
  }

  @Test
  fun `starts a new sentence after a full stop, a question mark or an exclamation mark`() {
    val found =
      sentences(
        listOf(
          Word(0.0, 0.5, " Tere."),
          Word(0.5, 1.0, " Kuidas"),
          Word(1.0, 1.5, " läheb?"),
          Word(1.5, 2.0, " Hästi!"),
          Word(2.0, 2.5, " Jah"),
        ),
      )
    expect(found.map { it.text }).toEqual(listOf("Tere.", "Kuidas läheb?", "Hästi!", "Jah"))
  }

  @Test
  fun `hears no words and no sentences in an endpoint without tokens`() {
    expect(Pair(words(emptyArray(), FloatArray(0), 0.0), sentences(emptyList()))).toEqual(Pair(emptyList(), emptyList()))
  }

  @Test
  fun `times a sentence from its first word to its last`() {
    val found = sentences(listOf(Word(0.0, 0.5, " Tere."), Word(0.5, 1.0, " Kuidas"), Word(1.0, 1.5, " läheb?")))
    expect(Pair(found[1].start, found[1].end)).toEqual(Pair(0.5, 1.5))
  }
}
