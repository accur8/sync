package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.{JsonCodec, JsonTypedCodec}
import a8.shared.json.ast.JsStr


trait EnumCompanion[A <: enumeratum.EnumEntry] { self: enumeratum.Enum[A] =>

  implicit lazy val rowReader: RowReader[A] =
    RowReader.stringReader.map(s => self.lowerCaseNamesToValuesMap(s.toLowerCase))

  implicit lazy val rowWriter: RowWriter[A] =
    RowWriter.stringWriter.mapWriter(e => e.entryName.toLowerCase)

  implicit lazy val jsonCodec: JsonTypedCodec[A,JsStr] =
    JsonCodec.string.dimap[A](s => self.lowerCaseNamesToValuesMap(s.toLowerCase), _.entryName.toLowerCase)

  implicit val sqlStringer: SqlStringer[A] =
    new SqlStringer[A] {
      override def toSqlString(a: A): SqlString =
        SqlString.escapedString(a.entryName)
    }

}
