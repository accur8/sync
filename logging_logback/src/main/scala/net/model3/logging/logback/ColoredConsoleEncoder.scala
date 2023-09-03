package net.model3.logging.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.pattern.{ExtendedThrowableProxyConverter, ThrowableProxyConverter}
import ch.qos.logback.classic.spi.{ILoggingEvent, IThrowableProxy}
import ch.qos.logback.core.encoder.{Encoder, EncoderBase}
import org.fusesource.jansi.Ansi

import java.nio.ByteBuffer
import java.time.temporal.TemporalField
import java.time.{LocalDateTime, OffsetDateTime}

class ColoredConsoleEncoder extends EncoderBase[ILoggingEvent] {

  implicit class AnsiOps(ansi: Ansi) {

    def render(opt: Option[String]): Ansi =
      opt match {
        case Some(s) =>
          ansi.render(s)
        case None =>
          ansi
      }

  }

//  11:19:50,828 | DEBUG | main | [] | m3.ServiceDiscovery | discovered the following services for string-converters -- List((100,syncro.db.SyncroStringConverters@4ef5dd23))

  val throwableProxyConverter = new ThrowableProxyConverter
//  val throwableProxyConverter = new ExtendedThrowableProxyConverter


  lazy val coloredLevels: Map[Level,Ansi.Color] =
    Map(
      Level.ERROR -> Ansi.Color.RED,
      Level.WARN  -> Ansi.Color.YELLOW,
      Level.INFO  -> Ansi.Color.BLUE,
      Level.DEBUG -> Ansi.Color.CYAN,
      Level.TRACE -> Ansi.Color.GREEN,
    )

  lazy val separator = " | "


  override def start(): Unit = {
    super.start()
    throwableProxyConverter.setContext(getContext)
    throwableProxyConverter.start()
  }

  override def headerBytes(): Array[Byte] =
    Array.empty[Byte]

  override def encode(event: ILoggingEvent): Array[Byte] = {
    val ndc = Option(event.getMDCPropertyMap.get("ndc")).getOrElse("")

    val levelColor =
      if ( event.getLevel.isGreaterOrEqual(Level.WARN) ) {
        Ansi.Color.RED
      } else {
        Ansi.Color.DEFAULT
      }

    val ansi =
      (new Ansi)
        .fg(levelColor)
        .render(timeStr(event))
        .render(separator)
        .fg(coloredLevels(event.getLevel))
        .render(event.getLevel.levelStr)
        .fg(levelColor)
        .render(separator)
        .render(event.getThreadName)
        .render(separator)
        .render("[")
        .render(ndc)
        .render("]")
        .render(separator)
        .render(event.getLoggerName)
        .render(separator)
        .render(event.getMessage)

    stackStrace(event)
      .map { st =>
        val indentedSt = st.linesIterator.map("       " + _).mkString("\n")
        ansi.newline().render(indentedSt)
      }

    ansi.newline().toString.getBytes

  }

  def stackStrace(event: ILoggingEvent): Option[String] = {
    if (event.getThrowableProxy != null) {
      Some(throwableProxyConverter.convert(event))
    } else {
      None
    }
  }


  def timeStr(event: ILoggingEvent): String = {
    val instant = event.getInstant
    val millis = instant.toEpochMilli % 1000
    val ldt = LocalDateTime.ofInstant( instant, OffsetDateTime.now().getOffset())
    f"${ldt.getHour}%02d:${ldt.getMinute}%02d:${ldt.getSecond}%02d.${millis}%03d"
  }


  override def footerBytes(): Array[Byte] =
    Array.empty[Byte]
}
