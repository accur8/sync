package a8.sync

import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf.{Conn, Row, RowReader, SchemaName, SqlString, TableLocator, TableName, unsafe}
import a8.sync.Imports._
import cats.effect.{Async, Resource}

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import a8.shared.json.DynamicJson
import a8.shared.json.ast._

import scala.concurrent.duration.Duration

object DocumentLoader {

  object RowToJsObj {
    implicit val rowReader =
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

  case class CacheableLoader[F[_] : Async](cacheDuration: Duration, loader: DocumentLoader[F]) extends AbstractDocumentLoader[F] {

    val F = Async[F]

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

    def load = {
      F.defer {
        cachedValue match {
          case Some(value) =>
            F.pure(value)
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

  def cache[F[_] : Async](loader: DocumentLoader[F], cacheDuration: Option[Duration]): DocumentLoader[F] = {
    cacheDuration
      .map(d => CacheableLoader[F](d, loader))
      .getOrElse(loader)
  }

  def queryIntoMap[F[_] : Async](connFactory: Resource[F, Conn[F]], keyExpr: SqlString, valueExpr: SqlString, schemaName: Option[SchemaName], tableName: TableName, whereExpr: Option[SqlString] = None, cacheDuration: Option[Duration] = None): DocumentLoader[F] = {
    val queryLoader =
      new AbstractDocumentLoader[F] {
        override def load: F[JsVal] = {
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

  def query[F[_] : Async](connFactory: Resource[F, Conn[F]], schemaName: Option[SchemaName], tableName: TableName, whereExpr: Option[SqlString] = None, cacheDuration: Option[Duration] = None): DocumentLoader[F] = {
    val queryLoader =
      new AbstractDocumentLoader[F] {
        override def load: F[JsVal] = {
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


  def jobject[F[_] : Async](fields: (String,DocumentLoader[F])*):  DocumentLoader[F]  = {
    val vfields = fields.toVector
    new AbstractDocumentLoader[F] {
      override def load: F[JsVal] =
        vfields
          .traverse { case (fieldName, loader) =>
            loader.load.map(fieldName -> _)
          }
          .map(v => JsObj(v.toMap))
    }
  }

  abstract class AbstractDocumentLoader[F[_] : Async] extends DocumentLoader[F] {
    def loadAsDynamicJson = load.map(DynamicJson.apply)

  }

}


sealed trait DocumentLoader[F[_]] {
  def load: F[JsVal]
  def loadAsDynamicJson: F[DynamicJson]
}
