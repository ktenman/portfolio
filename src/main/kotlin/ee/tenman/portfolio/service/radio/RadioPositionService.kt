package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.repository.RadioPositionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RadioPositionService(
  private val radioPositionRepository: RadioPositionRepository,
) {
  @Transactional(readOnly = true)
  fun find(
    listener: String,
    recording: String,
  ): RadioPositionDto = RadioPositionDto(radioPositionRepository.findByListenerAndRecording(listener, recording)?.seconds ?: 0.0)

  @Transactional
  fun save(
    listener: String,
    recording: String,
    position: RadioPositionDto,
  ) = radioPositionRepository.upsert(listener, recording, position.seconds)
}
