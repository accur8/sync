package a8.sync.qubes

import a8.shared.jdbcf.SqlString.SqlStringer
import cats.effect.Async

/**
 *
 *needs to have the apps space and cube name
 *
 */
trait QubesKeyedMapper[A,B] extends QubesMapper[A] {

 def fetch[F[_] : QubesApiClient : Async](b: B)(implicit sqlStringer: SqlStringer[B]): F[A]
 def fetchOpt[F[_] : QubesApiClient : Async](b: B)(implicit sqlStringer: SqlStringer[B]): F[Option[A]]

}
