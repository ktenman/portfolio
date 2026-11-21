package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.RadioPlayer
import ee.tenman.portfolio.repository.RadioPlayerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RadioPlayerService(
  private val radioPlayerRepository: RadioPlayerRepository,
  private val radioArchiveService: RadioArchiveService,
) {
  fun find(listener: String): RadioPlayerDto {
    val player = radioPlayerRepository.findByListener(listener) ?: return RadioPlayerDto(null, null, null, null)
    return RadioPlayerDto(player.recording?.let(radioArchiveService::recording), player.station, spot(player), player.rate)
  }

  @Transactional
  fun open(
    listener: String,
    choice: RadioPlayerChoice,
  ) = radioPlayerRepository.upsertChoice(listener, choice.recording, choice.station?.name)

  @Transactional
  fun place(
    listener: String,
    spot: RadioSpot,
  ) = radioPlayerRepository.upsertSpot(listener, spot.x, spot.y)

  @Transactional
  fun pace(
    listener: String,
    rate: RadioRate,
  ) = radioPlayerRepository.upsertRate(listener, rate.rate)

  private fun spot(player: RadioPlayer): RadioSpot? {
    val x = player.x ?: return null
    val y = player.y ?: return null
    return RadioSpot(x, y)
  }
}
