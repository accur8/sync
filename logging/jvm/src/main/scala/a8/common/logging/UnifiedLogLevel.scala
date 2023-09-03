package a8.common.logging


object UnifiedLogLevel {

  val All = UnifiedLogLevel(Level.All)
  val Trace = UnifiedLogLevel(Level.Trace)
  val Debug = UnifiedLogLevel(Level.Debug)
  val Info = UnifiedLogLevel(Level.Info)
  val Warn = UnifiedLogLevel(Level.Warn)
  val Error = UnifiedLogLevel(Level.Error)
  val Fatal = UnifiedLogLevel(Level.Fatal)
  val Off = UnifiedLogLevel(Level.Off)

  def apply(a8LogLevel: Level): UnifiedLogLevel = {
    val zioLogLevel =
      a8LogLevel match {
        case Level.All =>
          zio.LogLevel.All
        case Level.Trace =>
          zio.LogLevel.Trace
        case Level.Debug =>
          zio.LogLevel.Debug
        case Level.Info =>
          zio.LogLevel.Info
        case Level.Warn =>
          zio.LogLevel.Warning
        case Level.Error =>
          zio.LogLevel.Error
        case Level.Fatal =>
          zio.LogLevel.Fatal
        case Level.Off =>
          zio.LogLevel.None
      }
    UnifiedLogLevel(a8LogLevel, zioLogLevel)
  }
}

case class UnifiedLogLevel(a8LogLevel: Level, zioLogLevel: zio.LogLevel) {

  lazy val isTrace = a8LogLevel == Level.Trace

  lazy val resolvedA8LogLevel: Level = {
    if (isTrace)
      Level.All
    else
      a8LogLevel
  }

}
