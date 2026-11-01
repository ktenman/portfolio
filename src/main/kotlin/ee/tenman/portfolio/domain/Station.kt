package ee.tenman.portfolio.domain

import java.time.Duration

const val RATE = 16000

enum class Station(
  val label: String,
  val bitrate: Int,
  val lag: Duration,
  val plan: String,
  val heartbeat: String,
  val prefix: String,
) {
  KUKU("Kuku", 256_000, Duration.ofSeconds(20), "plan.json", "heartbeat.json", ""),
  ARIPAEV("Äripäev", 128_000, Duration.ofSeconds(20), "plan-aripaev.json", "heartbeat-aripaev.json", "aripaev-"),
  ;

  val bytesPerHour: Long = (bitrate / 8 + RATE * 2L) * 3600
}
