package a8.shared.jdbcf.mapper


import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.ast.JsDoc
import a8.shared.SharedImports._

object JsDocMapper {

  object stringBacked {

    implicit val rowReader: a8.shared.jdbcf.RowReader[JsDoc] =
      implicitly[RowReader[Option[String]]]
        .map {
          case None =>
            JsDoc.empty
          case Some(s) =>
            json.unsafeParse(s).toDoc
        }

    implicit val rowWriter: a8.shared.jdbcf.RowWriter[JsDoc] =
      RowWriter.stringWriter.mapWriter[JsDoc](_.compactJson)

    implicit val jsdocSqlStringer: a8.shared.jdbcf.SqlString.SqlStringer[JsDoc] =
      new SqlStringer[JsDoc] {
        override def toSqlString(a: JsDoc): SqlString =
          SqlString.escapedString(a.compactJson)
      }

  }


  object jsonbBacked {

    implicit val rowReader: a8.shared.jdbcf.RowReader[JsDoc] =
      stringBacked.rowReader

    implicit val rowWriter: a8.shared.jdbcf.RowWriter[JsDoc] =
      RowWriter.stringWriter.mapWriter[JsDoc](_.compactJson)

    implicit val jsdocSqlStringer: a8.shared.jdbcf.SqlString.SqlStringer[JsDoc] =
      new SqlStringer[JsDoc] {
        override def toSqlString(a: JsDoc): SqlString =
          SqlString.keyword(SqlString.escapedString(a.compactJson).toString + "::jsonb")
      }

  }


}
