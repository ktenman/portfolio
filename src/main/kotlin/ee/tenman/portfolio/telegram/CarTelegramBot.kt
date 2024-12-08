package ee.tenman.portfolio.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tenman.portfolio.auto24.Auto24Service
import ee.tenman.portfolio.configuration.TimeUtility
import ee.tenman.portfolio.googlevision.GoogleVisionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.net.URI

/**
 * Telegram bot for processing car-related queries and images.
 * Supports license plate detection from images and car price estimation.
 * Commands:
 * - ark {plateNumber} : Look up car price by plate number
 * - car {plateNumber} : Alternative command for price lookup
 * Image processing: Supports JPEG and PNG formats
 */
@Service
class CarTelegramBot(
  @Value("\${telegram.bot.token}") private val botToken: String,
  private val googleVisionService: GoogleVisionService,
  private val auto24Service: Auto24Service,
  private val objectMapper: ObjectMapper
) : TelegramLongPollingBot(botToken) {

  private val log = LoggerFactory.getLogger(javaClass)
  private val commandRegex = Regex("^(ark|car)\\s+([0-9]{3}[A-Za-z]{3})\\b", RegexOption.IGNORE_CASE)
  private val supportedImageTypes = setOf("image/jpeg", "image/png")

  @Deprecated("Deprecated in Java")
  override fun getBotToken(): String = botToken
  override fun getBotUsername(): String = "CarTelegramBot"

  override fun onUpdateReceived(update: Update) {
    val startTime = System.nanoTime()
    if (!update.hasMessage()) return
    val message = update.message

    try {
      processMessage(message, startTime)
    } catch (e: Exception) {
      log.error("Error processing message", e)
      sendMessage(message.chatId.toString(), "Error: ${e.message}", message.messageId)
    }
  }

  private fun processMessage(message: Message, startTime: Long) {
    val chatId = message.chatId.toString()
    val messageId = message.messageId

    when {
      message.hasPhoto() -> message.photo.maxByOrNull { it.fileSize }?.let { photo ->
        processImageOrDocument(downloadTelegramFile(photo.fileId), chatId, messageId, startTime)
      }
      message.hasDocument() && supportedImageTypes.contains(message.document?.mimeType) ->
        processImageOrDocument(downloadTelegramFile(message.document.fileId), chatId, messageId, startTime)
      message.hasText() -> commandRegex.find(message.text)?.groupValues?.get(2)?.uppercase()?.let { plateNumber ->
        log.info("Processing plate number: $plateNumber")
        lookupAndSendCarPrice(plateNumber, chatId, messageId, startTime)
      }
      else -> sendMessage(chatId, "Unsupported message type. Send an image or use 'car XXX123' command.", messageId)
    }
  }

  private fun processImageOrDocument(imageFile: File, chatId: String, replyToMessageId: Int, startTime: Long) = try {
    val visionResult = googleVisionService.getPlateNumber(imageFile)
    log.debug("Vision result: {}", objectMapper.writeValueAsString(visionResult))

    when {
      visionResult["hasCar"] == "false" -> sendMessage(chatId, "No car detected.", replyToMessageId)
      visionResult["plateNumber"] == null -> sendMessage(chatId, "No license plate detected.", replyToMessageId)
      else -> lookupAndSendCarPrice(visionResult["plateNumber"].toString(), chatId, replyToMessageId, startTime)
    }
  } finally {
    imageFile.delete()
  }

  private fun lookupAndSendCarPrice(plateNumber: String, chatId: String, replyToMessageId: Int, startTime: Long): Any? {
    val carPrice = auto24Service.findCarPrice(plateNumber).replace("kuni", "to")
    val duration = TimeUtility.durationInSeconds(startTime)
    return sendMessage(chatId, "Plate: $plateNumber\nEstimated price: $carPrice\nDuration: $duration seconds", replyToMessageId)
  }

  private fun downloadTelegramFile(fileId: String): File = execute(GetFile().apply { this.fileId = fileId })
    .let { tgFile ->
      File.createTempFile("telegram_", "_file").apply {
        URI.create(fileUrl(tgFile.filePath)).toURL().openStream().use { input ->
          outputStream().use { output -> input.copyTo(output) }
        }
      }
    }

  private fun fileUrl(filePath: String) = "https://api.telegram.org/file/bot${getBotToken()}/$filePath"

  private fun sendMessage(chatId: String, text: String, replyToMessageId: Int? = null) = try {
    execute(SendMessage().apply {
      this.chatId = chatId
      this.text = text
      replyToMessageId?.let { this.replyToMessageId = it }
    })
  } catch (e: TelegramApiException) {
    log.error("Failed to send message: {}", text, e)
  }
}
