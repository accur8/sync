package net.model3.logging.logback


import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.util.FileSize
import net.model3.logging.logback.RollingFileAppender.Kind

import java.io.FileOutputStream
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths}
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.annotation.tailrec
import scala.concurrent.{Future, blocking}
import scala.jdk.CollectionConverters.*
import a8.common.logging.{Logger, LoggerFactory, LoggingBootstrapConfig}

import java.util.concurrent.atomic.AtomicBoolean
import java.time.LocalDate
import scala.concurrent.duration.Duration
import a8.common.logging.LoggingIoOps.*

import java.util.concurrent.{Executors, TimeUnit}

object RollingFileAppender {

  val threadScheduler = Executors.newScheduledThreadPool(1)

  object Kind {
    val details = Kind(".details", Level.ALL)
    val errors = Kind(".errors", Level.WARN)
  }

  case class Kind(
    suffix: String,
    level: Level,
  )

}

/**
 */
class RollingFileAppender extends OutputStreamAppender[ILoggingEvent] {

  given CanEqual[LocalDate, LocalDate] = CanEqual.derived

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val rollOnNext = new AtomicBoolean(false)
  var activeFile: Path = _
  var currentDate = LocalDate.now()

  var maxFileSize: FileSize = FileSize.valueOf("25mb")
  var maxArchiveSize: FileSize = FileSize.valueOf("1gb")
  var maxAgeInDays: Int = 30
  var logDir: Path = LoggingBootstrapConfig.globalBootstrapConfig.logsDirectory.getAbsoluteFile.toPath
  var archiveDir: Path = LoggingBootstrapConfig.globalBootstrapConfig.archivesDirectory.getAbsoluteFile.toPath
  var filenamePrefix: String = LoggingBootstrapConfig.globalBootstrapConfig.appName.toLowerCase
  var kind: RollingFileAppender.Kind = RollingFileAppender.Kind.details

  var checkEvery: Duration = Duration(60, "seconds")

  def setMaxArchiveSize(value: FileSize): Unit =
    maxArchiveSize = value

  def setMaxAgeInDays(value: Int): Unit =
    maxAgeInDays = value

  def setLogDir(value: String): Unit =
    logDir = Paths.get(value)

  def setArchiveDir(value: String): Unit =
    archiveDir = Paths.get(value)

  def setFilenamePrefix(value: String): Unit =
    filenamePrefix = value

  def setKind(value: String): Unit = {
    kind =
      value.trim.toLowerCase match {
        case "error" | "errors" =>
          Kind.errors
        case "detail" | "details" =>
          Kind.details
        case v =>
          addWarn(s"invalid kind ${v} defaulting to details")
          Kind.details
      }
  }

  def filter(): ch.qos.logback.classic.filter.ThresholdFilter =
    new ch.qos.logback.classic.filter.ThresholdFilter { self =>
      setLevel(kind.level.levelStr)
      self.start()
    }

  override def start(): Unit = {

//    for testing
//    maxFileSize = FileSize.valueOf("1")
//    maxArchiveSize = FileSize.valueOf("1 mb")
//    checkEvery = TimeDuration.fromSeconds(60)

    activeFile = logDir.resolve(filename)
    if ( activeFile.exists ) {
      submitCleanup(moveCurrentFile())
    } else {
      submitCleanup(None)
    }
    addFilter(filter())
    openOutputStream()
    scheduleRolloverChecks()
    super.start()
  }

  def filename = filenamePrefix + kind.suffix

  def withStreamWriteLock[A](fn: =>A): A = {
    streamWriteLock.lock()
    try {
      fn
    } finally {
      streamWriteLock.unlock()
    }
  }

  override protected def writeOut(event: ILoggingEvent): Unit = {
    withStreamWriteLock {
      if ( rollOnNext.get() ) {
        doRollover()
      }
      super.writeOut(event)
    }
  }

  def doRollover() = {
    closeOutputStream()
    submitCleanup(moveCurrentFile())
    openOutputStream()
    rollOnNext.set(false)
  }

  private def openOutputStream(): Unit = {
    if ( !activeFile.getParent.exists )
      activeFile.getParent.toFile.mkdirs()
    setOutputStream(new FileOutputStream(activeFile.toFile))
  }

  def moveCurrentFile(): Option[Path] = {
    // move current file into the archives folder
    if ( activeFile.exists ) {
      if ( !archiveDir.isDirectory ) {
        archiveDir.toFile.mkdirs()
      }
      val af = ArchivedFile(activeFile)
      val originalTarget = archiveDir.resolve(activeFile.getFileName.toString + "." + fileSystemCompatibleTimestampStr(af.creationTime))
      @tailrec
      def ensureTargetDoesNotExist(path: Path, index: Int): Path = {
        if ( path.exists ) {
          ensureTargetDoesNotExist(Paths.get(originalTarget.toString + "." + index), index+1)
        } else {
          path
        }
      }

      val target = ensureTargetDoesNotExist(originalTarget, 1)
      Files.move(activeFile, target)
      Some(target)
    } else {
      None
    }
  }

  def submitCleanup(rolledFile: Option[Path]): Future[Unit] =
    LogbackLoggerFactory.loggingConfiguredFuture.map{ _ => blocking {

      val logger = Logger.logger(getClass)

      rolledFile
        .foreach { path =>
          logger.debug(s"gzip'ing ${path}")
          val process =
            scala.sys.process.Process(Seq("gzip", path.toAbsolutePath.toString))
              .run(false)
//              .execCaptureOutput(false)
          if ( process.exitValue() > 0 ) {
            sys.error(s"gzip ${path.toAbsolutePath.toString} failed")
          } else {
            ()
          }
        }

      val archivedFiles = listFilesInArchive()

      val (expiredFiles, nonExpiredFiles) = archivedFiles.partition(_.ageInDays > maxAgeInDays)

      expiredFiles
        .foreach { af =>
          logger.debug(s"purging expired ${af.file}")
          af.file.delete()
        }

      val totalSize =
        nonExpiredFiles
          .map(af => af.size)
          .foldLeft(0L)(_ + _)

      @tailrec
      def purge(spaceNeeded: Long, candidates: List[ArchivedFile]): Unit = {
        candidates.headOption match {
          case None =>
            ()
          case Some(af) if spaceNeeded > 0 =>
            logger.debug(s"purging ${af.file} for maxArchiveSize of ${maxArchiveSize}")
            af.file.delete()
            purge(spaceNeeded - af.size, candidates.tail)
          case _ =>
            ()
        }
      }

      val sortedNonExpiredFiles =
        nonExpiredFiles
          .sortBy(_.creationTime)
          .reverse
          .toList

      val amountToPurge = totalSize - maxArchiveSize.getSize
      purge(amountToPurge, nonExpiredFiles)

    }}

  case class ArchivedFile(file: Path) {

    lazy val creationTime = file.creationTime

    /** age in days */
    lazy val ageInDays: Int = {
      val now = LocalDateTime.now()
      ChronoUnit.DAYS.between(creationTime, now).toInt
    }

    lazy val size = file.size()

  }

  def listFilesInArchive(): List[ArchivedFile] = {
    Files
      .list(archiveDir)
      .iterator()
      .asScala
      .filter { f =>
        val filename = f.getFileName.toString
        filename.startsWith(filenamePrefix) && filename.contains(kind.suffix) && f.isFile
      }
      .map { p => ArchivedFile(p) }
      .toList
  }

  def scheduleRolloverChecks(): Future[Unit] = {
    LogbackLoggerFactory
      .loggingConfiguredFuture
      .map { _ =>
        val runnable = new Runnable {
          override def run(): Unit = {
            try {
              doRolloverCheck()
            } catch {
              case e: Exception =>
                Logger.logger(getClass).info("error in doRolloverCheck", e)
            }
          }
        }
        RollingFileAppender
          .threadScheduler
          .scheduleWithFixedDelay(
            runnable,
            checkEvery.toMillis,
            checkEvery.toMillis,
            TimeUnit.MILLISECONDS,
          )
      }
  }

  def doRolloverCheck(): Unit = {
    try {
      if (activeFile.exists && activeFile.size() > maxFileSize.getSize) {
        rollOnNext.set(true)
      } else {
        val today = LocalDate.now()
        if ( today != currentDate ) {
          currentDate = today
          rollOnNext.set(true)
        }
      }
    } catch {
      case e: Exception =>
        Logger.logger(getClass).error(e)
    }
  }

  def fileSystemCompatibleTimestampStr(ldt: LocalDateTime = LocalDateTime.now()): String = {
    f"${ldt.getYear}%04d${ldt.getMonth.getValue}%02d${ldt.getDayOfMonth}%02d_${ldt.getHour}%02d${ldt.getMinute}%02d${ldt.getSecond}%02d"
  }

}
