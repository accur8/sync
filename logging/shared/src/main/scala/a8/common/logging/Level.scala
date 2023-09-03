package a8.common.logging

object Level {
  given CanEqual[Level, Level] = CanEqual.derived
}

enum Level extends Ordered[Level] {

  case All, Trace, Debug, Info, Warn, Error, Fatal, Off

  lazy val name = productPrefix
  lazy val nameUpper = name.toUpperCase
  lazy val nameLower = name.toLowerCase

  override def compare(that: Level): Int =
    ordinal.compare(that.ordinal)

}
