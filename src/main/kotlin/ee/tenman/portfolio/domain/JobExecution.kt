package ee.tenman.portfolio.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "job_execution")
class JobExecution(
  @Column(nullable = false)
  var jobName: String,

  @Column(nullable = false)
  var startTime: Instant,

  @Column(nullable = false)
  var endTime: Instant,

  @Column(nullable = false)
  var durationInMillis: Long,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: JobStatus,

  @Column(columnDefinition = "TEXT")
  var message: String? = null
) : BaseEntity()
