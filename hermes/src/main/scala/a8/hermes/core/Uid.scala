package a8.hermes.core

import java.util.{Base64, UUID}

/**
 * UID generator matching godev's implementation.
 *
 * Algorithm:
 * 1. Generate UUID (128 bits = 16 bytes)
 * 2. Convert to byte array
 * 3. Base64 URL-safe encode (no padding)
 * 4. Remove -, =, and _ characters (leaving only alphanumeric)
 * 5. Accumulate characters until desired length is reached
 */
object Uid {

  /**
   * Generate a UID of the specified length.
   *
   * Matches godev's uid.Generate(length) function.
   */
  def generate(length: Int): String = {
    val builder = new StringBuilder()
    builder.sizeHint(length)

    while (builder.length < length) {
      val cleanChars = generateCleanChars()
      val remaining = length - builder.length

      if (cleanChars.length <= remaining) {
        // Use all characters
        builder.append(cleanChars)
      } else {
        // Use only what we need
        builder.append(cleanChars.substring(0, remaining))
      }
    }

    builder.toString
  }

  /**
   * Generate a 20-character UID
   */
  def uid20(): String = generate(20)

  /**
   * Generate a 32-character UID (standard for mailbox keys)
   */
  def uid32(): String = generate(32)

  /**
   * Generate a 48-character UID
   */
  def uid48(): String = generate(48)

  /**
   * Generate a 64-character UID
   */
  def uid64(): String = generate(64)

  /**
   * Generate clean alphanumeric characters from a UUID.
   *
   * 1. Generate UUID
   * 2. Convert to 16 bytes
   * 3. Base64 URL-safe encode (no padding)
   * 4. Remove -, =, and _ characters
   *
   * Matches godev's generateCleanChars() function.
   */
  private def generateCleanChars(): String = {
    // Generate UUID
    val uuid = UUID.randomUUID()

    // Convert to 16 bytes
    val bytes = new Array[Byte](16)
    val msb = uuid.getMostSignificantBits
    val lsb = uuid.getLeastSignificantBits

    // Pack MSB (8 bytes)
    for (i <- 0 until 8) {
      bytes(i) = ((msb >> (8 * (7 - i))) & 0xFF).toByte
    }

    // Pack LSB (8 bytes)
    for (i <- 0 until 8) {
      bytes(8 + i) = ((lsb >> (8 * (7 - i))) & 0xFF).toByte
    }

    // Base64 URL-safe encode without padding
    val encoded = Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)

    // Remove -, =, and _ characters (leaving only alphanumeric)
    encoded
      .replace("-", "")
      .replace("=", "")
      .replace("_", "")
  }

}
