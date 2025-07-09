package a8.shared.jdbcf


import java.sql.{Connection, PreparedStatement, ResultSet, Statement}
import a8.shared.SharedImports.*
import a8.common.logging.Logging
import a8.shared.app.Ctx
import a8.shared.app.Ctx.Completion
import a8.shared.zreplace.Resource

object Managed extends Logging {

  abstract class AbstractManaged[A] extends Managed[A] {
    override def safeClose(a: A): Unit = {
      try {
        if (!isClosed(a)) {
          close(a)
        }
      } catch {
        case th: Throwable =>
          logger.debug(s"catching and swallowing error in safeClose(${a}) this is likely benign", th)
      }
    }
  }

  object impl {

    def doSafely(thunk: =>Unit): Unit = {
      try {
        thunk
      } catch {
        case th: Throwable =>
          logger.debug(s"catching and swallowing error in doSafely, this is likely benign", th)
      }
    }

    def create[A](isClosedFn: A=>Boolean, closeFn: A=>Unit, cancelFn: Option[A=>Unit] = None): Managed[A] = {
      val isCancelable0 = cancelFn.isDefined
      new AbstractManaged[A] {
        override def complete(completion: Completion, a: A): Unit = {
          safeClose(a)
        }
        override def cancel(a: A): Unit = {
          doSafely(
            cancelFn
              .map(fn => fn(a))
          )
        }

        val isCancelable = isCancelable0
        override def isClosed(a: A): Boolean =
          isClosedFn(a)
        override def close(a: A): Unit =
          doSafely(closeFn(a))
      }
    }
  }
  import impl._

  implicit val connection: Managed[java.sql.Connection] =
    new AbstractManaged[Connection] {

      override val isCancelable: Boolean = false
      override def cancel(conn: Connection): Unit = ()

      override def complete(completion: Completion, conn: Connection): Unit = {
        if (!conn.getAutoCommit && !conn.isClosed) {
          if ( !completion.isSuccess ) {
            val exitName = {
              if (completion.isCancelled) "canceled"
              else if (completion.isThrown) "failed"
              else "success"
            }
            logger.debug(s"Transaction ${conn}. Rolling back and closing connection.")
            conn.rollback()
          } else {
            conn.commit()
          }
          safeClose(conn)
        }
      }

      override def isClosed(conn: Connection): Boolean =
        conn.isClosed()

      override def close(conn: Connection): Unit =
        conn.close()

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

  def resource[A : Managed](thunk: Ctx ?=> A): Resource[A] = {
    val managed = implicitly[Managed[A]]
    Resource.acquireRelease(
      acquire = thunk
    )(
      release = a => {
        if (!managed.isClosed(a)) {
          managed.close(a)
        }
      }
    )
  }

  def resourceWithContext[A : Managed](contextOpt: Option[String])(thunk: =>A): Resource[A] = {
    !!!
//    val managed = implicitly[Managed[A]]
//
//    val acquire: ZIO[Any, Throwable, A] =
//      ZIO
//        .attemptBlocking(thunk)
//        .onError( th =>
//          contextOpt match {
//            case Some(context) =>
//              loggerF.debug(s"error acquiring resource with context ${context}", th)
//            case None =>
//              ZIO.unit
//          }
//        )
//
//    def release(a: A, exit: Exit[Any,Any]): ZIO[Any,Nothing,Unit] =
//      managed.complete(exit, a)
//
//    ZIO.acquireReleaseExit(acquire)(release)
  }

  def scoped[A : Managed](thunk: =>A): Resource[A] =
    resourceWithContext(None)(thunk)

}

trait Managed[A] {
  val isCancelable: Boolean
  def cancel(a: A): Unit
  def complete(completion: Completion, a: A): Unit
  def isClosed(a: A): Boolean
  def close(a: A): Unit

  /**
   * only closes if isClosed = false
   */
  def safeClose(a: A): Unit
}
