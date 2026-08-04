package net.model3.logging.logback

import ch.qos.logback.core.joran.spi.NoAutoStart
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP

import java.io.File

@NoAutoStart
class RollOnStartupTriggeringPolicy[E] extends SizeAndTimeBasedFNATP[E] {

  var firstTime = true


  @NoAutoStart
  override def start(): Unit =
    super.start()

  override def isTriggeringEvent(activeFile: File, event: E): Boolean = {
    if ( firstTime ) {
      firstTime = false
      atomicNextCheck.set(0)
      super.isTriggeringEvent(activeFile, event)
      true
    } else {
      super.isTriggeringEvent(activeFile, event)
    }
  }

  override def getElapsedPeriodsFileName: String =
    "bob.bob"
}
