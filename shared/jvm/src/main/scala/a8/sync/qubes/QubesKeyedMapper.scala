package a8.sync.qubes


import a8.shared.jdbcf.SqlString.SqlStringer
import zio.Task

/**
 *
 *needs to have the apps space and cube name
 *
 */
trait QubesKeyedMapper[A,B] extends QubesMapper[A] {

 def fetch(b: B)(implicit sqlStringer: SqlStringer[B], qubesApiClient: QubesApiClient): Task[A]
 def fetchOpt(b: B)(implicit sqlStringer: SqlStringer[B], qubesApiClient: QubesApiClient): Task[Option[A]]

}
