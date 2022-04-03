package a8.sync.auditlog


import a8.shared.jdbcf.{Batcher, Conn, Dialect, Row, RowReader, RowWriter, SqlString}
import a8.sync.ResolvedTable.ColumnMapper.{BooleanNormalValue, JsonNormalValue, NumberNormalValue, StringNormalValue, TimestampNormalValue}
import a8.shared.jdbcf.{ColumnName, TableLocator, TableName, TypeName}
import a8.sync.impl.NormalizedValue
import java.time.{LocalDate, LocalDateTime}

import a8.shared.SingleArgConstructor
import fs2.Stream
import org.postgresql.util.PGobject
import org.typelevel.ci.CIString
import a8.sync.Imports._
import a8.sync.Utils
import a8.shared.StringValue.{CIStringValue, CIStringValueCompanion, Companion}
import a8.shared.jdbcf.SqlString._

object AuditLog {

  sealed trait RowAction
  object RowAction {
    case object Insert extends RowAction
    case object Update extends RowAction
    case object Delete extends RowAction

    implicit val rowMapper: RowReader[RowAction] =
      RowReader.singleColumnReader[RowAction] {
        case "i" => Insert
        case "u" => Update
        case "d" => Delete
      }

  }

  sealed trait AuditRowApplyResult
  object AuditRowApplyResult {
    case object Success extends AuditRowApplyResult
    case object NotFound extends AuditRowApplyResult
  }

  object Version extends SingleArgConstructor.Companion[Long,Version] {

    implicit val rowMapper: RowReader[Version] =
      RowReader.singleColumnReader[Version] {
        case i: java.lang.Number =>
          Version(i.longValue())
      }

  }
  case class Version(value: Long)


  def autoNormalize(a: AnyRef): NormalizedValue =
    a match {
      case None =>
        NormalizedValue.Null
      case s: String =>
        StringNormalValue(s)
      case n: java.lang.Number =>
        NumberNormalValue(BigDecimal(n.toString))
      case b: java.lang.Boolean =>
        BooleanNormalValue(b)
      case ts: java.sql.Timestamp =>
        TimestampNormalValue(ts)
      case pgo: PGobject if pgo.getType == "json" || pgo.getType == "jsonb" =>
        JsonNormalValue(pgo.getValue, TypeName(pgo.getType))
      case addMe =>
        throw new RuntimeException(s"currently do not autoNormalize class: ${addMe.getClass.toString}. Add me")
    }

  object AuditLogRow {
    implicit val reader: RowReader[AuditLogRow] = {


      new RowReader[AuditLogRow] {

        override def rawRead(row: Row, index: Int): (AuditLogRow, Int) = {

          val auditLogRow: Map[ColumnName, NormalizedValue] =
            row
              .asMap
              .filterNot{ rowM =>
                List(
                  "audit_version",
                  "audit_action",
                  "audit_start",
                  "audit_end",
                  "audit_previous_version",
                  "audit_next_version"
                ).contains(rowM._1.toString)
              }
              .map { t =>
                ColumnName(t._1) -> autoNormalize(t._2)
              }

          AuditLogRow(
            version = row.get[Version](s"audit_version"),
            action = row.get[RowAction](s"audit_action"),
            start = row.get[LocalDateTime](s"audit_start"),
            end = row.opt[LocalDateTime](s"audit_end"),
            previousVersion = row.opt[Version](s"audit_previous_version"),
            nextVersion = row.opt[Version](s"audit_next_version"),
            row = auditLogRow,
          ) -> row.asMap.size

        }
      }
    }
  }
  case class AuditLogRow(
    version: Version,
    action: RowAction,
    start: LocalDateTime,
    end: Option[LocalDateTime],
    previousVersion: Option[Version],
    nextVersion: Option[Version],
    row: Map[ColumnName, NormalizedValue],
  ) {
    def updateQuery(targetTable: TableName, primaryKey: ColumnName)(implicit dialect: Dialect): SqlString = {
      import RowAction._
      action match {
        case Insert =>
          sql"insert into ${targetTable} (${row.map(_._1.asSqlFragment).mkSqlString(sql",")}) VALUES(${row.map(_._2.asSqlFragment).mkSqlString(sql",")}) ON CONFLICT (${primaryKey}) DO NOTHING"
        case Update =>
          sql"update ${targetTable} set ${row.map(f => sql"${f._1} = ${f._2.asSqlFragment}").mkSqlString(sql",")} where ${primaryKey} = ${row(primaryKey).asSqlFragment}"
        case Delete =>
          sql"delete from ${targetTable} where ${primaryKey} = ${row(primaryKey).asSqlFragment}"
      }
    }
  }


  def readAuditLog(
    sourceTable: TableName,
    start: Version,
    sourceConn: Conn[IO]
  )(
    implicit
      dialect: Dialect
  ): fs2.Stream[IO, AuditLogRow] = {
    sourceConn
      .streamingQuery[AuditLogRow](sql"""select * from ${sourceTable} where audit_version >= ${start.value} order by audit_version limit 99999""")
      .run
  }

  def applyAuditLogOrdered(
    auditStream: fs2.Stream[IO, AuditLogRow],
    primaryKey: ColumnName,
    targetTable: TableName,
    targetConn: Conn[IO],
  )(
    implicit
      dialect: Dialect
  ): fs2.Stream[IO, (AuditRowApplyResult,Version)] = {
    auditStream
      .evalMap { row =>
        targetConn
          .update(row.updateQuery(targetTable, primaryKey))
          .map { i =>
            (i match {
              case 0 => AuditRowApplyResult.NotFound
              case 1 => AuditRowApplyResult.Success
            }) -> row.version
          }
      }
  }

  def applyAuditLogFastUnordered(
    auditStream: fs2.Stream[IO, AuditLogRow],
    primaryKey: ColumnName,
    targetTable: TableName,
    targetConn: Conn[IO],
  )(
    implicit
      dialect: Dialect,
      cache: Data,
  ): IO[fs2.Stream[IO,Int]] = {
//  ): fs2.Stream[IO, (AuditRowApplyResult,Version)] = {
    targetConn
      .tableMetadata(TableLocator(targetTable))
      .map { tableMeta =>

        assert(tableMeta.columns.nonEmpty)
        assert(tableMeta.keys.nonEmpty)

        val tableColumns = tableMeta.columns
        val primaryKeys = tableMeta.keys

        import a8.shared.jdbcf.SqlString._

        val insertSql = q"insert into ${targetTable} (${tableColumns.map(_.name.asSqlFragment).mkSqlString(q",")}) VALUES(${tableColumns.map(_ => q"?").mkSqlString(q",")})" // ON CONFLICT (${primaryKey.sqlValue}) DO NOTHING"
        val updateSql = q"update ${targetTable} set ${tableColumns.map(c => q"${c.name} = ?").mkSqlString(q",")} where ${primaryKeys.map(pk => q"${pk.name} = ?").mkSqlString(q" and ")}"
        val deleteSql = q"delete from ${targetTable} where ${primaryKeys.map(pk => q"${pk.name} = ?").mkSqlString(q" and ")}"

        val insertWriter: RowWriter[AuditLogRow] =
          RowWriter.createx[AuditLogRow] { (ps, parameterIndex, row) =>
            tableColumns.foreach { column =>
              row.row(column.name).prepare(ps, column.ordinalPosition+parameterIndex)
            }
          }

        val deleteWriter: RowWriter[AuditLogRow] =
          RowWriter.createx[AuditLogRow] { (ps, parameterIndex, row) =>
            primaryKeys.foreach { column =>
              row.row(column.name).prepare(ps, column.jdbcPrimaryKey.get.keyIndex+parameterIndex-1)
            }
          }

        val updateWriter: RowWriter[AuditLogRow] =
          RowWriter.createx[AuditLogRow] { (ps, parameterIndex, row) =>
            insertWriter.applyParameters(ps, row, parameterIndex)
            deleteWriter.applyParameters(ps, row, parameterIndex+tableColumns.size)
          }


        val insertBatcher: Batcher[IO, AuditLogRow] = targetConn.batcher(insertSql)(insertWriter)
        val updateBatcher: Batcher[IO, AuditLogRow] = targetConn.batcher(updateSql)(updateWriter)
        val deleteBatcher: Batcher[IO, AuditLogRow] = targetConn.batcher(deleteSql)(deleteWriter)

        import RowAction._

        (
          insertBatcher.execBatch(auditStream.filter(_.action == Insert))
            ++ updateBatcher.execBatch(auditStream.filter(_.action == Update))
            ++ deleteBatcher.execBatch(auditStream.filter(_.action == Delete))
        )

      }
  }



}