package a8.sync


import a8.sync.ResolvedTable.ResolvedField
import a8.sync.impl.{NormalizedKey, NormalizedRow, NormalizedTuple, NormalizedValue}
import Imports._
import a8.shared.jdbcf.{Dialect, SqlString}
import SqlString._
import a8.sync.ResolvedTable.ColumnMapper.StringNormalValue
import a8.sync.RowSync.ValidationMessage
import a8.sync.dsl.TruncateAction
import cats.data.Chain
import wvlet.log.LogLevel
import zio.Chunk

import scala.collection.mutable

//  sealed trait SyncAction
//  object SyncAction {
//    case object Insert extends SyncAction
//    case object Update extends SyncAction
//    case object Delete extends SyncAction
//    case object Noop extends SyncAction
//  }

object RowSync {

  type ValidationMessage = (LogLevel,String)

  object impl {

    def autoTruncate(field: ResolvedField, value: NormalizedValue): NormalizedValue = {
      value match {
        case StringNormalValue(s) =>
          StringNormalValue(s.take(field.jdbcColumn.columnSize))
        case _ =>
          value
      }
    }

    def syncToSql(sync: RowSync): SqlString = {

      implicit def dialect: Dialect = sync.table.dialect

      import sync.table
      import sync.table.keyFieldsByIndex
      import sync.table.resolvedFields

      def tupleUp(fields: Chunk[ResolvedField], values: NormalizedTuple, fieldSeparator: SqlString)(fn: (ResolvedField, NormalizedValue) => SqlString): SqlString =
        fields
          .iterator
          .zip(values.iterator)
          .filter {
            case (_, NormalizedValue.Omit) => false
            case _ => true
          }
          .map { t =>
            fn(t._1, autoTruncate(t._1, t._2))
          }
          .mkSqlString(fieldSeparator)

      def whereClause: SqlString =
        tupleUp(keyFieldsByIndex, sync.key.values, q" and ") { (f, v) =>
          q"""${f} = ${v}"""
        }

      sync match {
        case v: RowSync.Insert =>
          val insertColumnNames =
            tupleUp(resolvedFields, v.truncatedNewRow.values, q", ") { (f, v) =>
              f
            }
          val insertValues =
            tupleUp(resolvedFields, v.truncatedNewRow.values, q", ") { (f, v) =>
              v
            }
          q"insert into ${table.qualifiedTargetTable} (${insertColumnNames}) values (${insertValues})"
        case v: RowSync.Update =>
          val setClause =
            tupleUp(resolvedFields, v.truncatedNewRow.values, q", ") { (f, v) =>
              q"""${f} = ${v}"""
            }
          q"update ${table.qualifiedTargetTable} set ${setClause} where ${whereClause}"
        case v: RowSync.Delete =>
          q"delete from ${table.qualifiedTargetTable} where ${whereClause}"
      }
    }

    def validateRow(untruncatedRow: NormalizedRow, defaultTruncationAction: TruncateAction, table: ResolvedTable): (Chain[ValidationMessage],NormalizedRow) = {
      val validationMessages = mutable.Buffer[ValidationMessage]()
      val truncatedValues =
        table
          .resolvedFields
          .iterator
          .zip(untruncatedRow.values.iterator)
          .map {
            case (resolvedField, untruncatedValue) =>
              val truncatedValue = autoTruncate(resolvedField, untruncatedValue)
              if ( truncatedValue != untruncatedValue ) {
                val truncateAction = resolvedField.field.truncateAction.getOrElse(defaultTruncationAction)
                val logLevelOpt = resolvedField.field.truncateAction.getOrElse(defaultTruncationAction).logLevel
                (logLevelOpt, untruncatedValue) match {
                  case (Some(logLevel), StringNormalValue(s)) =>
                    validationMessages.append(
                      logLevel -> s"${table.qualifiedTargetTable.toString}.${resolvedField.jdbcColumn.columnName.value} truncated from length ${s.length} to ${resolvedField.jdbcColumn.columnSize} original value is -- '${s}'"
                    )
                  case (Some(logLevel), v) =>
                    sys.error(s"don't know how to handle truncated value that is not a StringNormalValue ${v}")
                  case _ =>
                }
              }
              truncatedValue
          }
          .toChunk
      validationMessages.iterator.toChain -> untruncatedRow.copy(values = truncatedValues)
    }

  }

  case class Insert(truncatedNewRow: NormalizedRow, validationMessages: Chain[ValidationMessage], untruncatedNewRow: NormalizedRow)(table0: ResolvedTable) extends RowSync {
    val table = table0
    val key = truncatedNewRow.key
  }

  case class Update(truncatedNewRow: NormalizedRow, oldRow: NormalizedRow, validationMessages: Chain[ValidationMessage], untruncatedNewRow: NormalizedRow)(table0: ResolvedTable) extends RowSync {
    val table = table0
    val key = truncatedNewRow.key

    lazy val isNoop =
      truncatedNewRow
        .values
        .iterator
        .zip(oldRow.values.iterator)
        .zip(table0.resolvedFields.iterator)
        .forall {
          case ((NormalizedValue.Omit, _), _) =>
            true
          case ((newValue, oldValue), field) =>
            newValue == oldValue
        }

  }

  case class Delete(oldRow: NormalizedRow, validationMessages: Chain[ValidationMessage])(table0: ResolvedTable) extends RowSync {
    val table = table0
    val key = oldRow.key
  }

}

sealed trait RowSync {

  val table: ResolvedTable
  val key: NormalizedKey
  val validationMessages: Chain[ValidationMessage]

  def updateSql: Option[SqlString] =
    this match {
      case u: RowSync.Update if u.isNoop =>
        None
      case _ =>
        RowSync.impl.syncToSql(this).toSome
    }

}
