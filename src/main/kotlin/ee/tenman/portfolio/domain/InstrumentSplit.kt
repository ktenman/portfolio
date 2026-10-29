package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "instrument_split")
class InstrumentSplit(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  val instrument: Instrument,
  @Column(nullable = false)
  val ratio: String,
  @Column(nullable = false)
  val effectiveDate: LocalDate,
  @Column(nullable = false, columnDefinition = "TEXT")
  val sourceUrl: String,
  val publishedDate: LocalDate? = null,
) : BaseEntity()
