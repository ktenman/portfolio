package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import org.springframework.scheduling.TaskScheduler
import java.time.Clock

@ScheduledJob
class RadioScheduleJob(
  kukuScheduleClient: KukuScheduleClient,
  radioScheduleService: RadioScheduleService,
  jobExecutionService: JobExecutionService,
  taskScheduler: TaskScheduler,
  clock: Clock,
  radioProperties: RadioProperties,
) : AmsScheduleJob(
    Station.KUKU,
    "kuku.pleier.ee",
    kukuScheduleClient,
    radioScheduleService,
    jobExecutionService,
    taskScheduler,
    clock,
    radioProperties,
  )
