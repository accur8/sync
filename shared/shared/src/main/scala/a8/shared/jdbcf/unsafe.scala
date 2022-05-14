package a8.shared.jdbcf

import zio.Chunk
import java.sql.ResultSet

import a8.shared.json.ast._
import a8.shared.SharedImports._

object unsafe {

  def coerceBoolean(b: Any): Option[Boolean] = {
    b match {
      case null => None
      case s: String => s.toLowerCase match {
        case "y" | "true" | "yes" | "1" => Some(true)
        case _ => Some(false)
      }
      case b: java.lang.Boolean => Some(b)
    }
  }


  def resultSetToIterator(resultSet: ResultSet): Iterator[Row] = {

    lazy val dsm =
      Row.Metadata(
        (1 to resultSet.getMetaData.getColumnCount).map(i => resultSet.getMetaData.getColumnName(i)).toVector
      )

    lazy val iter = {
      new Iterator[Row] {
        def hasNext = resultSet.next()

        def next() = {
          val columnCount = resultSet.getMetaData.getColumnCount
          val array = new Array[AnyRef](columnCount)
          (1 to columnCount) foreach { i =>
            val v0 = resultSet.getObject(i)
            val v =
              if ( v0 == null || resultSet.wasNull()) {
                None
              } else
                v0
            array(i - 1) = v
          }
          Row(Chunk.fromArray(array), dsm)
        }
      }
    }

    iter

  }


  def coerceToJsVal(a: AnyRef): JsVal = {
    a match {
      case s: String =>
        JsStr(s)
      case null =>
        JsNull
      case sbd: scala.math.BigDecimal =>
        JsNum(sbd)
      case bd: java.math.BigDecimal =>
        JsNum(BigDecimal(bd))
      case b: java.lang.Boolean =>
        JsBool(b)
      case n: java.lang.Number =>
        JsNum(BigDecimal(n.toString))
      case t: java.sql.Timestamp =>
        JsStr(t.toString)
      case d: java.sql.Date =>
        JsStr(d.toString)
      case t: java.sql.Time =>
        JsStr(t.toString)
      case jv: JsVal =>
        jv
      //      case po: PGobject if po.getType == "json" || po.getType == "jsonb" => jsonApi.parseJson(po.getValue)
      case other =>
        sys.error(s"toJsVal needs a case for ${other}, ${other.getClass}")
    }
  }

}
