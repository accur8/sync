package a8.shared


import a8.shared.FileSystem.Directory
import a8.shared.NamedToString
import a8.shared.app.Logging
import a8.shared.jdbcf.ISeriesDialect.logger

import java.io.{ByteArrayOutputStream, PrintWriter}

object Exec {

  def apply(args: String*): Exec =
    Exec(args, None)

  case class Result(
    exitCode: Int,
    stdout: String,
    stderr: String
  ) extends NamedToString

}

case class Exec(
  args: Iterable[String],
  workingDirectory: Option[Directory] = None
)
  extends NamedToString
  with Logging
{

  def inDirectory(directory: Directory): Exec =
    copy(workingDirectory = Some(directory))

  import Exec._

  private def _process =
    sys.process.Process(args.toSeq, workingDirectory.map(d => new java.io.File(d.canonicalPath)))

  def execCaptureOutput(failOnNonZeroExitCode: Boolean = true): Result = {
    import sys.process._
    val stdout = new ByteArrayOutputStream
    val stderr = new ByteArrayOutputStream
    val stdoutWriter = new PrintWriter(stdout)
    val stderrWriter = new PrintWriter(stderr)
    logger.info(toString)
    val exitCode = _process.!(ProcessLogger(stdoutWriter.println, stderrWriter.println))
    stdoutWriter.close()
    stderrWriter.close()
    val result = Result(
      exitCode = exitCode,
      stdout = stdout.toString,
      stderr = stderr.toString
    )
    if ( failOnNonZeroExitCode && exitCode != 0 )
      sys.error(s"error \n    ${this}\n    ${result}")
    result
  }

  /**
    * will output to stdout and stderr
    */
  def execInline(failOnNonZeroExitCode: Boolean = true): Int = {
    logger.info(toString)
    val exitCode = _process.!
    if ( failOnNonZeroExitCode && exitCode != 0 )
      sys.error(s"error ${this} exitCode = ${exitCode}")
    exitCode
  }

  lazy val argsAsString: String =
    args
      .map { arg =>
        if ( arg.exists(_.isWhitespace) ) s"'${arg}'"
        else arg
      }
      .mkString(" ")

  override def toString: String =
    s"running ${workingDirectory.map(d=>s"with a cwd of ${d}").getOrElse("")} the command -- ${argsAsString}"

}
