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
import org.telegram.telegrambots.meta.api.objects.Document
import org.telegram.telegrambots.meta.api.objects.PhotoSize
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

    val chatId = update.message.chatId.toString()
    val messageId = update.message.messageId // Capture the original message's ID

    try {
      when {
        update.message.hasPhoto() -> {
          val photo = update.message.photo.maxByOrNull { it.fileSize }
            ?: throw IllegalStateException("No photo found in message")
          processPhoto(photo, chatId, messageId)
        }

        update.message.hasDocument() -> {
          val document = update.message.document
          processDocument(document, chatId, messageId)
        }

        update.message.hasText() -> {
          processText(update.message.text, chatId, messageId)
        }
      }
    } catch (e: Exception) {
      log.error("Error processing message", e)
      sendMessage(chatId, "Error processing request: ${e.message}", messageId)
    }
  }

  private fun processText(text: String, chatId: String, replyToMessageId: Int) {
    val matchResult = commandRegex.find(text)
    if (matchResult != null) {
      val plateNumber = matchResult.groupValues[2].uppercase()
      log.info("Found plate number in text: $plateNumber")
      lookupAndSendCarPrice(plateNumber, chatId, replyToMessageId)
    }
  }

  private fun processPhoto(photo: PhotoSize, chatId: String, replyToMessageId: Int) {
    val photoFile = downloadPhoto(photo)
    try {
      val visionResult = googleVisionService.getPlateNumber(photoFile)
      log.info("Vision result: {}", objectMapper.writeValueAsString(visionResult))

      when {
        visionResult["hasCar"] == "false" -> {
          sendMessage(chatId, "No car detected in the image.", replyToMessageId)
        }

        visionResult["plateNumber"] == null -> {
          sendMessage(chatId, "No license plate detected in the image.", replyToMessageId)
        }

        else -> {
          val plateNumber = visionResult["plateNumber"]
          lookupAndSendCarPrice(plateNumber.toString(), chatId, replyToMessageId)
        }
      }
    } finally {
      photoFile.delete()
    }
  }

  private fun processDocument(document: Document, chatId: String, replyToMessageId: Int) {
    // Check if the document is an image
    // Common MIME types: image/jpeg, image/png, etc.
    val mimeType = document.mimeType ?: ""
    if (mimeType.startsWith("image/")) {
      val imageFile = downloadDocument(document)
      try {
        val visionResult = googleVisionService.getPlateNumber(imageFile)
        log.info("Vision result: {}", objectMapper.writeValueAsString(visionResult))

        when {
          visionResult["hasCar"] == "false" -> {
            sendMessage(chatId, "No car detected in the image.", replyToMessageId)
          }

          visionResult["plateNumber"] == null -> {
            sendMessage(chatId, "No license plate detected in the image.", replyToMessageId)
          }

          else -> {
            val plateNumber = visionResult["plateNumber"]!!.uppercase()
            lookupAndSendCarPrice(plateNumber, chatId, replyToMessageId)
          }
        }
      } finally {
        imageFile.delete()
      }
    } else {
      sendMessage(chatId, "This document is not a supported image (jpeg or png).", replyToMessageId)
    }
  }

  private fun lookupAndSendCarPrice(plateNumber: String, chatId: String, replyToMessageId: Int) {
    val price = auto24.findCarPrice(plateNumber)
    sendMessage(chatId, "Detected plate number: $plateNumber\nEstimated price: $price", replyToMessageId)
  }

  private fun downloadPhoto(photo: PhotoSize): File {
    val getFile = GetFile().apply { fileId = photo.fileId }
    val filePath = execute(getFile).filePath
    return downloadFileFromTelegram(filePath)
  }

  private fun downloadDocument(document: Document): File {
    val getFile = GetFile().apply { fileId = document.fileId }
    val filePath = execute(getFile).filePath
    return downloadFileFromTelegram(filePath)
  }

  private fun downloadFileFromTelegram(filePath: String): File {
    val fileUrl = "https://api.telegram.org/file/bot${getBotToken()}/$filePath"
    val tempFile = File.createTempFile("telegram_", "_file")
    URI.create(fileUrl).toURL().openStream().use { input ->
      tempFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    return tempFile
  }

  private fun sendMessage(chatId: String, text: String, replyToMessageId: Int? = null) {
    val message = SendMessage().apply {
      this.chatId = chatId
      this.text = text
      if (replyToMessageId != null) {
        this.replyToMessageId = replyToMessageId
      }
    }
    try {
      execute(message)
    } catch (e: TelegramApiException) {
      log.error("Failed to send message: {}", text, e)
    }
  }

}
