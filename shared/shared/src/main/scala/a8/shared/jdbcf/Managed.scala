package a8.shared.jdbcf


import java.sql.{Connection, PreparedStatement, ResultSet, Statement}
import a8.shared
import a8.shared.SharedImports
import a8.shared.SharedImports._
import cats.effect.kernel.Resource
import cats.effect.kernel.Resource.ExitCase
import wvlet.log.LazyLogger


object Managed extends LazyLogger {

  implicit def logger0 = logger

  abstract class AbstractManaged[A] extends Managed[A] {
    override def safeClose[F[_] : Sync](a: A): F[Unit] =
      isClosed[F](a)
        .flatMap {
          case true =>
            Sync[F].unit
          case false =>
            close[F](a)
        }
  }

  object impl {
    def create[A](isClosedFn: A=>Boolean, closeFn: A=>Unit, cancelFn: Option[A=>Unit] = None): Managed[A] = {
      val isCancelable0 = cancelFn.isDefined
      new AbstractManaged[A] {
        override def complete[F[_] : Sync](exitCase: ExitCase, a: A): F[Unit] = safeClose(a)
        override def cancel[F[_] : Sync](a: A): F[Unit] =
          cancelFn
            .map(fn => Sync[F].blocking(fn(a)))
            .getOrElse(Sync[F].unit)
        val isCancelable = isCancelable0
        override def isClosed[F[_] : Sync](a: A): F[Boolean] = Sync[F].blocking(isClosedFn(a))
        override def close[F[_] : Sync](a: A): F[Unit] = Sync[F].blocking(closeFn(a))
      }
    }
  }
  import impl._

  implicit val connection: Managed[java.sql.Connection] =
    new AbstractManaged[Connection] {

      override val isCancelable: Boolean = false
      override def cancel[F[_] : Sync](conn: Connection): F[Unit] = Sync[F].unit

      override def complete[F[_] : shared.SharedImports.Sync](exitCase: ExitCase, conn: Connection): F[Unit] = {
        Sync[F].blocking {
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

      override def isClosed[F[_] : Sync](conn: Connection): F[Boolean] =
        Sync[F].blocking(conn.isClosed)

      override def close[F[_] : Sync](conn: Connection): F[Unit] =
        Sync[F].blocking(conn.close())

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

  def resource[F[_] : Sync, A : Managed](thunk: =>A): Resource[F, A] = {
    val Managed = implicitly[Managed[A]]
    Resource.applyCase(
      Sync[F]
        .blocking {
          val resource = thunk
          resource -> { ec: ExitCase => Managed.complete[F](ec, resource) }
        }
    )
  }

  def resourceWithContext[F[_] : Async, A : Managed](context: String)(thunk: =>A): Resource[F, A] = {
    val Managed = implicitly[Managed[A]]
    val F = Async[F]
    Resource.applyCase(
      F.blocking {
          val resource = thunk
          resource -> { ec: ExitCase => Managed.complete[F](ec, resource) }
        }
        .onError  {
          case IsNonFatal(th) =>
            F.blocking(
              logger.warn(s"error acquiring resource with context ${context}", th)
            )
        }
    )
  }

  def stream[F[_] : Sync, A : Managed](thunk: =>A): fs2.Stream[F, A] = {
    val managed = Managed[A]
    val baseStream = fs2.Stream.resource(resource[F, A](thunk))
    if ( managed.isCancelable ) {
      baseStream
        .flatMap { a =>
          fs2.Stream.emit(a)
            .onFinalizeCase {
              case ExitCase.Canceled =>
                managed.cancel(a)
              case _ =>
                Sync[F].unit
            }
        }
    } else {
      baseStream
    }
  }

}

trait Managed[A] {
  val isCancelable: Boolean
  def cancel[F[_] : Sync](a: A): F[Unit]
  def complete[F[_] : Sync](exitCase: ExitCase, a: A): F[Unit]
  def isClosed[F[_] : Sync](a: A): F[Boolean]
  def close[F[_] : Sync](a: A): F[Unit]

  /**
   * only closes if isClosed = false
   */
  def safeClose[F[_] : Sync](a: A): F[Unit]
}
