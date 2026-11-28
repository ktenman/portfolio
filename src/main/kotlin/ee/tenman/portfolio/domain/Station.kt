package ee.tenman.portfolio.domain

import java.time.Duration

enum class Station(
  val label: String,
  val bitrate: Int,
  val lag: Duration,
  val bridged: Boolean = false,
) {
  KUKU("Kuku", 256_000, Duration.ofSeconds(20)),
  ARIPAEV("Äripäev", 128_000, Duration.ofSeconds(30), bridged = true),
  ELMAR("Elmar", 256_000, Duration.ofSeconds(20), bridged = true),
  SKYPLUS("Sky Plus", 320_000, Duration.ofSeconds(20), bridged = true),
  RAADIO2("Raadio 2", 130_000, Duration.ofSeconds(10)),
  VIKERRAADIO("Vikerraadio", 130_000, Duration.ofSeconds(10)),
  ;

  private val slug: String? get() = name.lowercase().takeIf { this != KUKU }
  val plan: String get() = slug?.let { "plan-$it.json" } ?: "plan.json"
  val heartbeat: String get() = slug?.let { "heartbeat-$it.json" } ?: "heartbeat.json"
  val prefix: String get() = slug?.let { "$it-" }.orEmpty()
  val playlist: String? get() = ".${name.lowercase()}/stream.m3u8".takeIf { bridged }
}
