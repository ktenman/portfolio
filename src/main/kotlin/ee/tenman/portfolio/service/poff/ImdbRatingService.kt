package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.imdb.ImdbDatasetClient
import ee.tenman.portfolio.repository.PoffImdbRepository
import org.springframework.stereotype.Service

@Service
class ImdbRatingService(
  private val imdbDatasetClient: ImdbDatasetClient,
  private val poffImdbRepository: PoffImdbRepository,
) {
  fun matched(): Boolean = poffImdbRepository.existsByImdbIdIsNotNull()

  fun refresh() {
    val rows = poffImdbRepository.findByImdbIdIsNotNull()
    if (rows.isEmpty()) return
    val ratings = readRatings(imdbDatasetClient.ratings(), rows.mapNotNull { it.imdbId }.toSet())
    rows.forEach { row ->
      val votes = ratings[row.imdbId]
      row.rating = votes?.rating
      row.votes = votes?.votes
    }
    poffImdbRepository.saveAll(rows)
  }
}
