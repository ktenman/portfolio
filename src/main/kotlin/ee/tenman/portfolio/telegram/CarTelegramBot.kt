package ee.tenman.portfolio.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tenman.portfolio.auto24.Auto24
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

@Service
class CarTelegramBot(
  @Value("\${telegram.bot.token}") private val botToken: String,
  private val googleVisionService: GoogleVisionService,
  private val auto24: Auto24,
  private val objectMapper: ObjectMapper
) : TelegramLongPollingBot(botToken) {

  private val log = LoggerFactory.getLogger(javaClass)
  private val commandRegex = Regex("^(ark|car)\\s+([0-9]{3}[A-Za-z]{3})\\b", RegexOption.IGNORE_CASE)

  override fun getBotToken(): String = botToken
  override fun getBotUsername(): String = "CarTelegramBot"

  override fun onUpdateReceived(update: Update) {
    if (!update.hasMessage()) return
    val message = update.message
    val chatId = message.chatId.toString()
    val messageId = message.messageId

    try {
      processMessage(message, chatId, messageId)
    } catch (e: Exception) {
      log.error("Error processing message", e)
      sendMessage(chatId, "Error processing request: ${e.message}", messageId)
    }
  }

  private fun processMessage(message: Message, chatId: String, messageId: Int) {
    when {
      message.hasPhoto() -> {
        val photo = message.photo.maxByOrNull { it.fileSize }
          ?: throw IllegalStateException("No photo found in message")
        processImageOrDocument(downloadTelegramFile(photo.fileId), chatId, messageId)
      }
      message.hasDocument() -> {
        val document = message.document
        if (document?.mimeType?.startsWith("image/") == true) {
          processImageOrDocument(downloadTelegramFile(document.fileId), chatId, messageId)
        } else {
          sendMessage(chatId, "This document is not a supported image (jpeg or png).", messageId)
        }
      }
      message.hasText() -> {
        val plateNumber = commandRegex.find(message.text)?.groupValues?.get(2)?.uppercase()
        if (plateNumber != null) {
          log.info("Found plate number in text: $plateNumber")
          lookupAndSendCarPrice(plateNumber, chatId, messageId)
        }
      }
    }
  }

  private fun processImageOrDocument(imageFile: File, chatId: String, replyToMessageId: Int) {
    try {
      val visionResult = googleVisionService.getPlateNumber(imageFile)
      log.info("Vision result: {}", objectMapper.writeValueAsString(visionResult))

      if (visionResult["hasCar"] == "false") {
        sendMessage(chatId, "No car detected in the image.", replyToMessageId)
        return
      }

      val plateNumber = visionResult["plateNumber"]
      if (plateNumber == null) {
        sendMessage(chatId, "No license plate detected in the image.", replyToMessageId)
        return
      }

      lookupAndSendCarPrice(plateNumber.toString(), chatId, replyToMessageId)
    } finally {
      imageFile.delete()
    }
  }

  private fun lookupAndSendCarPrice(plateNumber: String, chatId: String, replyToMessageId: Int) {
    val price = auto24.findCarPrice(plateNumber)
    sendMessage(chatId, "Detected plate number: $plateNumber\nEstimated price: $price", replyToMessageId)
  }

  private fun downloadTelegramFile(fileId: String): File {
    val tgFile = execute(GetFile().apply { this.fileId = fileId })
    return File.createTempFile("telegram_", "_file").apply {
      URI.create(fileUrl(tgFile.filePath)).toURL().openStream().use { input ->
        outputStream().use { output -> input.copyTo(output) }
      }
    }
  }

  private fun fileUrl(filePath: String) = "https://api.telegram.org/file/bot${getBotToken()}/$filePath"

  private fun sendMessage(chatId: String, text: String, replyToMessageId: Int? = null) {
    try {
      execute(SendMessage().apply {
        this.chatId = chatId
        this.text = text
        replyToMessageId?.let { this.replyToMessageId = it }
      })
    } catch (e: TelegramApiException) {
      log.error("Failed to send message: {}", text, e)
    }
  }
}
