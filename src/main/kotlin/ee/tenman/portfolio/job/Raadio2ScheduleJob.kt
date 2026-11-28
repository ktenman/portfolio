package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.err.ErrScheduleClient
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import org.springframework.scheduling.TaskScheduler
import java.time.Clock

@ScheduledJob
class Raadio2ScheduleJob(
  errScheduleClient: ErrScheduleClient,
  radioScheduleService: RadioScheduleService,
  jobExecutionService: JobExecutionService,
  taskScheduler: TaskScheduler,
  clock: Clock,
  radioProperties: RadioProperties,
) : ErrScheduleJob(
    Station.RAADIO2,
    errScheduleClient,
    radioScheduleService,
    jobExecutionService,
    taskScheduler,
    clock,
    radioProperties,
  )
