package ee.tenman.portfolio.testing.fixture

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageFixtures {
  fun createPngHeader(
    width: Int = 100,
    height: Int = 100,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
    buffer.put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    buffer.putInt(13)
    buffer.put(byteArrayOf(0x49, 0x48, 0x44, 0x52))
    buffer.putInt(width)
    buffer.putInt(height)
    return buffer.array()
  }

  fun createJpegHeader(
    width: Int = 100,
    height: Int = 100,
    sofMarker: Int = 0xC0,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
    buffer.put(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), sofMarker.toByte()))
    buffer.putShort(11)
    buffer.put(8)
    buffer.putShort(height.toShort())
    buffer.putShort(width.toShort())
    return buffer.array()
  }

  fun createVp8WebPHeader(
    width: Int = 100,
    height: Int = 100,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(byteArrayOf(0x52, 0x49, 0x46, 0x46))
    buffer.putInt(22)
    buffer.put(byteArrayOf(0x57, 0x45, 0x42, 0x50))
    buffer.put(byteArrayOf(0x56, 0x50, 0x38, 0x20))
    buffer.putInt(10)
    buffer.put(ByteArray(6))
    buffer.put((width and 0xFF).toByte())
    buffer.put(((width shr 8) and 0x3F).toByte())
    buffer.put((height and 0xFF).toByte())
    buffer.put(((height shr 8) and 0x3F).toByte())
    return buffer.array()
  }

  fun createVp8LWebPHeader(
    width: Int = 100,
    height: Int = 100,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(byteArrayOf(0x52, 0x49, 0x46, 0x46))
    buffer.putInt(18)
    buffer.put(byteArrayOf(0x57, 0x45, 0x42, 0x50))
    buffer.put(byteArrayOf(0x56, 0x50, 0x38, 0x4C))
    buffer.putInt(6)
    buffer.put(0x2F.toByte())
    val widthMinus1 = width - 1
    val heightMinus1 = height - 1
    val packed = widthMinus1 or (heightMinus1 shl 14)
    buffer.put((packed and 0xFF).toByte())
    buffer.put(((packed shr 8) and 0xFF).toByte())
    buffer.put(((packed shr 16) and 0xFF).toByte())
    buffer.put(((packed shr 24) and 0xFF).toByte())
    return buffer.array()
  }

  fun createVp8XWebPHeader(
    width: Int = 100,
    height: Int = 100,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(byteArrayOf(0x52, 0x49, 0x46, 0x46))
    buffer.putInt(22)
    buffer.put(byteArrayOf(0x57, 0x45, 0x42, 0x50))
    buffer.put(byteArrayOf(0x56, 0x50, 0x38, 0x58))
    buffer.putInt(10)
    buffer.putInt(0)
    val widthMinus1 = width - 1
    buffer.put((widthMinus1 and 0xFF).toByte())
    buffer.put(((widthMinus1 shr 8) and 0xFF).toByte())
    buffer.put(((widthMinus1 shr 16) and 0xFF).toByte())
    val heightMinus1 = height - 1
    buffer.put((heightMinus1 and 0xFF).toByte())
    buffer.put(((heightMinus1 shr 8) and 0xFF).toByte())
    buffer.put(((heightMinus1 shr 16) and 0xFF).toByte())
    return buffer.array()
  }

  fun createWebPWithSubformat(
    subformat: Byte,
    width: Int = 100,
    height: Int = 100,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(byteArrayOf(0x52, 0x49, 0x46, 0x46))
    buffer.putInt(22)
    buffer.put(byteArrayOf(0x57, 0x45, 0x42, 0x50))
    buffer.put(byteArrayOf(0x56, 0x50, 0x38))
    buffer.put(subformat)
    buffer.putInt(10)
    buffer.put(ByteArray(6))
    buffer.put((width and 0xFF).toByte())
    buffer.put(((width shr 8) and 0x3F).toByte())
    buffer.put((height and 0xFF).toByte())
    buffer.put(((height shr 8) and 0x3F).toByte())
    return buffer.array()
  }
}
