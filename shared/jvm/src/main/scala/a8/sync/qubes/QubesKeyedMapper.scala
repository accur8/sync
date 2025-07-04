package a8.sync.qubes


import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.SharedImports.*
import a8.shared.json.JsonReader.JsonReaderOptions
/**
 *
 *needs to have the apps space and cube name
 *
 */
trait QubesKeyedMapper[A,B] extends QubesMapper[A] {

 def fetch(b: B)(implicit sqlStringer: SqlStringer[B], qubesApiClient: QubesApiClient, jsonReaderOptions: JsonReaderOptions): Task[A]
 def fetchOpt(b: B)(implicit sqlStringer: SqlStringer[B], qubesApiClient: QubesApiClient, jsonReaderOptions: JsonReaderOptions): Task[Option[A]]

}
