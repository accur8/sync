package net.model3.logging.logback

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.core.spi.ContextAwareBase

class NoopLogbackConfigurator extends ContextAwareBase with Configurator { outer =>

  override def configure(context: LoggerContext): Configurator.ExecutionStatus = {
    context.reset()
    Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY
  }

}

