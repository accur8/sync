package a8.shared.jdbcf

opaque type Bytes = Array[Byte]

object Bytes {
  def apply(bytes: Array[Byte]): Bytes = bytes
  def fromString(s: String): Bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8)

  extension (b: Bytes) {
    def toArray: Array[Byte] = b
    def toUtf8String: String = new String(b, java.nio.charset.StandardCharsets.UTF_8)
  }
}
