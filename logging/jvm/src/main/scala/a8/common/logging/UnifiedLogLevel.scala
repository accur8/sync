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
    UnifiedLogLevel(a8LogLevel)
  }
}

case class UnifiedLogLevel(
  a8LogLevel: Level,
//  zioLogLevel: zio.LogLevel,
) {

  lazy val isTrace = a8LogLevel == Level.Trace

  lazy val resolvedA8LogLevel: Level = {
    if (isTrace)
      Level.All
    else
      a8LogLevel
  }

}
