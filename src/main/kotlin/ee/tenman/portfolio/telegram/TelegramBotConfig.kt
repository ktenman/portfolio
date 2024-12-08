package ee.tenman.portfolio.telegram

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Configuration
class TelegramBotConfig {
  @Bean
  fun telegramBotsApi(): TelegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)

  @Bean
  fun registerBot(bot: CarTelegramBot, api: TelegramBotsApi): TelegramBotsApi {
    api.registerBot(bot)
    return api
  }
}
