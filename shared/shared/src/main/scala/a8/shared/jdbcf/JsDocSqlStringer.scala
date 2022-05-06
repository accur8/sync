package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.ast.{JsDoc, JsNothing, JsNull}

case class JsDocSqlStringer(nullable: Boolean, typeSuffix: Option[String]) extends SqlStringer[JsDoc] {


  override def materialize[F[_]: Async](conn: Conn[F], resolvedColumn: JdbcMetadata.ResolvedColumn): F[SqlStringer[JsDoc]] = {

    val typeSuffix =
      conn
        .dialect
        .isPostgres
        .toOption("::"  + resolvedColumn.jdbcColumn.typeName)

    Async[F].pure(
      JsDocSqlStringer(resolvedColumn.isNullable, typeSuffix)
    )
  }

  /**
   * mildly lengthy method because it is optimized for each code path
   */
  override def toSqlString(jsd: JsDoc): SqlString = {
    lazy val empty =
      jsd.value match {
        case JsNull | JsNothing =>
          true
        case _ =>
          false
      }
    if ( nullable && empty ) {
      typeSuffix match {
        case None =>
          SqlString.Null
        case Some(s) =>
          SqlString.keyword("null" + s)
      }
    } else {
      typeSuffix match {
        case None =>
          SqlString.escapedString(jsd.compactJson)
        case Some(suffix) =>
          SqlString.keyword(SqlString.escapedString(jsd.compactJson).toString + suffix)
      }
    }
  }

}
