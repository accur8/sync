package a8.shared.jdbcf


import java.sql.{Connection, PreparedStatement, ResultSet, Statement}
import a8.shared.SharedImports._
import a8.shared.app.{Logging, LoggingF}
import zio.stream.{UStream, ZStream}
import zio._

object Managed extends Logging with LoggingF {

  abstract class AbstractManaged[A] extends Managed[A] {
    override def safeClose(a: A): UIO[Unit] =
      isClosed(a)
        .flatMap {
          case true =>
            ZIO.unit
          case false =>
            close(a)
        }
        .catchAll(th =>
          loggerF.debug(s"catching and swallowing error in safeClose(${a}) this is likely benign", th)
        )
  }

  object impl {
    def create[A](isClosedFn: A=>Boolean, closeFn: A=>Unit, cancelFn: Option[A=>Unit] = None): Managed[A] = {
      val isCancelable0 = cancelFn.isDefined
      new AbstractManaged[A] {
        override def complete(exitCase: Exit[Any, Any], a: A): ZIO[Any, Nothing, Unit] = safeClose(a)
        override def cancel(a: A): Task[Unit] =
          cancelFn
            .map(fn => ZIO.attemptBlocking(fn(a)))
            .getOrElse(ZIO.unit)
        val isCancelable = isCancelable0
        override def isClosed(a: A): Task[Boolean] = ZIO.attemptBlocking(isClosedFn(a))
        override def close(a: A): Task[Unit] = ZIO.attemptBlocking(closeFn(a))
      }
    }
  }
  import impl._

  implicit val connection: Managed[java.sql.Connection] =
    new AbstractManaged[Connection] {

      override val isCancelable: Boolean = false
      override def cancel(conn: Connection): Task[Unit] = ZIO.unit

      override def complete(exitCase: ExitCase, conn: Connection): Task[Unit] = {
        ZIO.attemptBlocking {
          trylogo("cleaning up completed connection") {
            if (!conn.getAutoCommit && !conn.isClosed) {
              exitCase match {
                case ExitCase.Canceled =>
                  logger.debug("Transaction canceled. Closing connection without commit.")
                case ExitCase.Errored(_) =>
                  logger.debug("Transaction failed. Rolling back and closing connection.")
                  conn.rollback()
                case ExitCase.Succeeded =>
                  conn.commit()
              }
            }
          }
        } >> safeClose(conn)
      }

      override def isClosed(conn: Connection): Task[Boolean] =
        ZIO.attemptBlocking(conn.isClosed)

      override def close(conn: Connection): Task[Unit] =
        ZIO.attemptBlocking(conn.close())

    }

  implicit val statement: Managed[Statement] =
    create[java.sql.Statement](
      _.isClosed,
      _.close(),
      Some(_.cancel())
    )

  implicit val preparedStatement: Managed[PreparedStatement] =
    create[java.sql.PreparedStatement](
      _.isClosed,
      _.close(),
      Some(_.cancel())
    )

  implicit val resultSet: Managed[ResultSet] =
    create[java.sql.ResultSet](
      _.isClosed,
      _.close()
    )

  def apply[A : Managed]: Managed[A] = implicitly[Managed[A]]

  def resource[A : Managed](thunk: =>A): ZIO[Scope,Throwable,A] = {
    val Managed = implicitly[Managed[A]]
    ZIO.acquireRelease(
      ZIO.attemptBlocking(thunk)
    )(
      a => Managed.complete(a)
    )
  }

  def resourceWithContext[A : Managed](context: String)(thunk: =>A): Resource[A] = {
    val managed = implicitly[Managed[A]]
    val acquire =
      ZIO
        .attemptBlocking(thunk)
        .catchAll { th =>
          ZIO.attemptBlocking(
            logger.warn(s"error acquiring resource with context ${context}", th)
          )
        }

    def release(a: A, exit :Exit[Any,Any]): ZIO[Any,Nothing,Unit] =
      managed.complete(exit, a)

    ZIO.acquireReleaseExit(acquire)(release)

//          val resource = thunk
//          resource -> { ec: ExitCase => Managed.complete(ec, resource) }
//        }
//        .onError  {
//          case IsNonFatal(th) =>
//            ZIO.attemptBlocking(
//              logger.warn(s"error acquiring resource with context ${context}", th)
//            )
//        }
//    )
  }

  def stream[A : Managed](thunk: =>A): UStream[A] = {
    val managed = Managed[A]
    val baseStream = ZStream.resource(resource[A](thunk))
    if ( managed.isCancelable ) {
      baseStream
        .flatMap { a =>
          ZStream.emit(a)
            .onFinalizeCase {
              case ExitCase.Canceled =>
                managed.cancel(a)
              case _ =>
                ZIO.unit
            }
        }
    } else {
      baseStream
    }
  }

}

trait Managed[A] {
  val isCancelable: Boolean
  def cancel(a: A): Task[Unit]
  def complete(exitCase: Exit[Any,Any], a: A): UIO[Unit]
  def isClosed(a: A): Task[Boolean]
  def close(a: A): Task[Unit]

  /**
   * only closes if isClosed = false
   */
  def safeClose(a: A): Task[Unit]
}
