package playground


import a8.shared.SharedImports._
import sttp.model.Uri

object DefaultSchemaDemo extends App {


  val uri: Uri = unsafeParseUri("jdbc:mysql:aws://dev-con-team-db.cluster-ckvahzu7ywiy.us-west-2.rds.amazonaws.com/sync-dev?sessionVariables=sql_mode=ANSI")

  println(uri.path.last)


}
