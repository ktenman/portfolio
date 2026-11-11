package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "poff_imdb")
class PoffImdb(
  @Column(nullable = false, unique = true)
  val filmId: Long,
  var imdbId: String?,
  var score: Int?,
  var rating: BigDecimal?,
  var votes: Int?,
  @Column(nullable = false)
  var triedAt: Instant,
) : BaseEntity()
