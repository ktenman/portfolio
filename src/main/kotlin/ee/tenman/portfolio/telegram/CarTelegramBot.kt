package ee.tenman.portfolio.telegram

import ee.tenman.portfolio.configuration.TimeUtility
import ee.tenman.portfolio.service.LicensePlateDetectionService
import ee.tenman.portfolio.service.VehicleInfoService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Telegram bot for processing car-related queries and images.
 * Supports license plate detection from images and car price estimation.
 * Commands:
 * - ark {plateNumber} : Look up car price by plate number
 * - car {plateNumber} : Alternative command for price lookup
 * Image processing: Supports JPEG and PNG formats
 */
@Service
@ConditionalOnProperty(
  name = ["telegram.bot.enabled"],
  havingValue = "true",
)
class CarTelegramBot(
  @Value("\${telegram.bot.token:}") private val botToken: String,
  @Value("\${telegram.bot.enabled:false}") private val botEnabled: Boolean,
  private val licensePlateDetectionService: LicensePlateDetectionService,
  private val vehicleInfoService: VehicleInfoService,
  private val objectMapper: ObjectMapper,
) : TelegramLongPollingBot(botToken) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val commandRegex = Regex("^(ark|car)\\s+([0-9]{3}[A-Za-z]{3})\\b", RegexOption.IGNORE_CASE)
  private val supportedImageTypes = setOf("image/jpeg", "image/png")

  companion object {
    private const val BOT_DISABLED_MESSAGE = "Telegram bot is disabled. No token provided."
  }

  private fun isBotDisabled() =
    (!botEnabled).also { disabled ->
      log.info(if (disabled) BOT_DISABLED_MESSAGE else "Telegram bot is enabled.")
    }

  @Deprecated("Deprecated in Java")
  override fun getBotToken(): String = botToken

  override fun getBotUsername(): String = "CarTelegramBot"

  override fun onUpdateReceived(update: Update) {
    if (isBotDisabled()) return

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

  private fun processMessage(
    message: Message,
    startTime: Long,
  ) {
    val chatId = message.chatId.toString()
    val messageId = message.messageId

    when {
      message.hasPhoto() ->
        message.photo.maxByOrNull { it.fileSize }?.let { photo ->
          processImageOrDocument(downloadTelegramFile(photo.fileId), chatId, messageId, startTime)
        }

      message.hasDocument() && supportedImageTypes.contains(message.document?.mimeType) ->
        processImageOrDocument(downloadTelegramFile(message.document.fileId), chatId, messageId, startTime)

      message.hasText() ->
        commandRegex.find(message.text)?.groupValues?.get(2)?.uppercase()?.let { plateNumber ->
          log.info("Processing plate number: $plateNumber")
          lookupAndSendCarPrice(plateNumber, chatId, messageId, startTime)
        }

      else -> sendMessage(chatId, "Unsupported message type. Send an image or use 'car XXX123' command.", messageId)
    }
  }

  private fun processImageOrDocument(
    imageFile: File,
    chatId: String,
    replyToMessageId: Int,
    startTime: Long,
  ) = try {
    val detectionResult = licensePlateDetectionService.detectPlateNumber(imageFile)
    log.debug("Detection result: {}", objectMapper.writeValueAsString(detectionResult))
    when {
      !detectionResult.hasCar -> sendMessage(chatId, "No car detected.", replyToMessageId)
      detectionResult.plateNumber == null ->
        sendMessage(chatId, "No license plate detected (provider: ${detectionResult.provider}).", replyToMessageId)
      else -> lookupAndSendCarPrice(detectionResult.plateNumber, chatId, replyToMessageId, startTime)
    }
  } finally {
    imageFile.delete()
  }

  private fun lookupAndSendCarPrice(
    plateNumber: String,
    chatId: String,
    replyToMessageId: Int,
    startTime: Long,
  ): Any? {
    val result = vehicleInfoService.getVehicleInfo(plateNumber)
    val duration = TimeUtility.durationInSeconds(startTime)
    val responseText = "${result.formattedText}\n\n⏱️  Duration: $duration seconds"
    return sendMessage(chatId, responseText, replyToMessageId)
  }

  private fun downloadTelegramFile(fileId: String): File {
    val fileInfo = execute(GetFile().apply { this.fileId = fileId })

    try {
      val tempDir = Files.createTempDirectory("telegram_app_")
      Files.setPosixFilePermissions(
        tempDir,
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
      )

      val tempFile = Files.createTempFile(tempDir, "telegram_", "_file")
      Files.setPosixFilePermissions(
        tempFile,
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
      )

      URI.create(fileUrl(fileInfo.filePath)).toURL().openStream().use { input ->
        Files.newOutputStream(tempFile).use { output ->
          input.copyTo(output)
        }
      }

      tempFile.toFile().deleteOnExit()
      tempDir.toFile().deleteOnExit()

      return tempFile.toFile()
    } catch (e: Exception) {
      log.error("Error downloading file from Telegram: ${e.message}", e)
      throw e
    }
  }

  private fun fileUrl(filePath: String) = "https://api.telegram.org/file/bot${getBotToken()}/$filePath"

  private fun sendMessage(
    chatId: String,
    text: String,
    replyToMessageId: Int? = null,
  ) = try {
    execute(
      SendMessage().apply {
        this.chatId = chatId
        this.text = text
        replyToMessageId?.let { this.replyToMessageId = it }
      },
    )
  } catch (e: TelegramApiException) {
    log.error("Failed to send message: {}", text, e)
  }
}
