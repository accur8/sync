package a8.sync


import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf.{Conn, Row, RowReader, SchemaName, SqlString, TableLocator, TableName, unsafe}
import a8.sync.Imports._

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import a8.shared.json.DynamicJson
import a8.shared.json.ast._

import scala.concurrent.duration.Duration
import zio._

object DocumentLoader {

  object RowToJsObj {
    implicit val rowReader: RowReader[RowToJsObj] =
      new RowReader[RowToJsObj] {
        override def rawRead(row: Row, index: Int): (RowToJsObj, Int) =
          RowToJsObj(
            row
              .subRow(index)
              .unsafeAsJsObj
          ) -> (row.size - index)
      }
  }
  case class RowToJsObj(value: JsObj)

  case class CacheableLoader(cacheDuration: Duration, loader: DocumentLoader) extends AbstractDocumentLoader {

    val lastLoad = new AtomicReference[Option[(LocalDateTime, JsVal)]](None)

    def cachedValue: Option[JsVal] =
      lastLoad.get match {
        case None =>
          None
        case Some((dt, value)) =>
          val now = LocalDateTime.now()
          val timeout = (dt + cacheDuration)
          if ( timeout.isBefore(now) ) {
            None
          } else {
            Some(value)
          }
      }

    def load: RIO[Any,JsVal] = {
      ZIO.suspend {
        cachedValue match {
          case Some(value) =>
            ZIO.succeed(value)
          case None =>
            loader
              .load
              .map { doc =>
                lastLoad.set(Some(LocalDateTime.now() -> doc))
                doc
              }
        }
      }
    }

  }

  def cache(loader: DocumentLoader, cacheDuration: Option[Duration]): DocumentLoader = {
    cacheDuration
      .map(d => CacheableLoader(d, loader))
      .getOrElse(loader)
  }

  def queryIntoMap(connFactory: Resource[Conn], keyExpr: SqlString, valueExpr: SqlString, schemaName: Option[SchemaName], tableName: TableName, whereExpr: Option[SqlString] = None, cacheDuration: Option[Duration] = None): DocumentLoader = {
    val queryLoader =
      new AbstractDocumentLoader {
        override def load: Task[JsVal] = {
          connFactory.use { conn =>
            val whereClause = whereExpr.map(e => q" where ${e}")
            val schemaPrefix = schemaName.map(sn => q"${sn}${conn.dialect.schemaSeparator}").getOrElse(q"")
            val sql = q"select ${keyExpr}, ${valueExpr} from ${schemaPrefix}${tableName}${whereClause}"
            conn
              .query[(String,JsVal)](sql)
              .select
              .map { rows =>
                JsObj(
                  rows.toMap
                )
              }
          }
        }
      }
    cache(queryLoader, cacheDuration)
  }

  def query(connFactory: Resource[Conn], schemaName: Option[SchemaName], tableName: TableName, whereExpr: Option[SqlString] = None, cacheDuration: Option[Duration] = None): DocumentLoader = {
    val queryLoader =
      new AbstractDocumentLoader {
        override def load: Task[JsVal] = {
          connFactory.use { conn =>
            for {
              resolvedJdbcTable <- conn.tableMetadata(TableLocator(schemaName, tableName))
              sql = resolvedJdbcTable.querySql(whereExpr)
              rowsToJsObj <-
                conn
                  .query[RowToJsObj](sql)
                  .select
            } yield JsArr(rowsToJsObj.map(_.value).toList)
          }
        }
      }

    cache(queryLoader, cacheDuration)
  }


  def jobject(fields: (String,DocumentLoader)*):  DocumentLoader  = {
    val vfields = fields.toVector
    new AbstractDocumentLoader {
      override def load: Task[JsVal] =
        vfields
          .map { case (fieldName, loader) =>
            loader
              .load
              .map(fieldName -> _)
          }
          .sequence
          .map(v => JsObj(v.toMap))
    }
  }

  abstract class AbstractDocumentLoader extends DocumentLoader {
    def loadAsDynamicJson: ZIO[Any,Throwable,DynamicJson] = load.map(DynamicJson.apply)

  }

}


sealed trait DocumentLoader {
  def load: Task[JsVal]
  def loadAsDynamicJson: Task[DynamicJson]
}
