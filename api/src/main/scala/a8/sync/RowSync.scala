package a8.sync


import a8.sync.ResolvedTable.ResolvedField
import a8.sync.impl.{NormalizedKey, NormalizedRow, NormalizedTuple, NormalizedValue}
import Imports.*
import a8.shared.jdbcf.{Dialect, SqlString}
import SqlString.*
import a8.common.logging.Level
import a8.sync.ResolvedTable.ColumnMapper.StringNormalValue
import a8.sync.RowSync.ValidationMessage
import a8.sync.dsl.TruncateAction
import cats.data.Chain
import zio.Chunk

import scala.annotation.nowarn
import scala.collection.mutable

//  sealed trait SyncAction
//  object SyncAction {
//    case object Insert extends SyncAction
//    case object Update extends SyncAction
//    case object Delete extends SyncAction
//    case object Noop extends SyncAction
//  }

/**
 * Companion object for [[RowSync]] containing implementation details and helper types.
 * 
 * RowSync represents the core synchronization operations for database row-level
 * synchronization between source and target databases.
 */
object RowSync {

  /**
   * Validation message type containing log level and message text.
   * 
   * Used to report issues during row synchronization, such as data truncation
   * or constraint violations.
   */
  type ValidationMessage = (Level,String)

  object impl {

    /**
     * Automatically truncates values to fit database column constraints.
     * 
     * Currently only handles string truncation based on column size limits.
     * Other data types are passed through unchanged.
     * 
     * @param field The field definition including column metadata
     * @param value The value to potentially truncate
     * @return The value, truncated if necessary
     */
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

    /**
     * Validates and potentially transforms a row before synchronization.
     * 
     * This method performs data validation and transformation including:
     * - Truncating string values that exceed column size limits
     * - Generating validation messages based on truncation actions
     * - Preserving original values for audit purposes
     * 
     * @param untruncatedRow The original row data before any transformations
     * @param defaultTruncationAction Default action when truncation is needed
     * @param table The target table metadata including column definitions
     * @return Tuple of (validation messages, transformed row)
     * 
     * @example
     * {{{
     * // With TruncateAction.Warn, strings are auto-truncated with warnings
     * val (messages, truncatedRow) = validateRow(
     *   originalRow, 
     *   TruncateAction.Warn,
     *   resolvedTable
     * )
     * 
     * messages.foreach { case (level, msg) =>
     *   logger.log(level, msg)
     * }
     * }}}
     */
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
                    ): @nowarn
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

  /**
   * Represents an INSERT operation for a new row.
   * 
   * @param truncatedNewRow The row with values truncated to fit column sizes
   * @param validationMessages Any validation issues encountered
   * @param untruncatedNewRow The original row before truncation
   * @param table0 The target table metadata
   */
  case class Insert(truncatedNewRow: NormalizedRow, validationMessages: Chain[ValidationMessage], untruncatedNewRow: NormalizedRow)(table0: ResolvedTable) extends RowSync {
    val table = table0
    val key = truncatedNewRow.key
  }

  /**
   * Represents an UPDATE operation for an existing row.
   * 
   * @param truncatedNewRow The new row values (truncated to fit)
   * @param oldRow The existing row values from the target database
   * @param validationMessages Any validation issues encountered
   * @param untruncatedNewRow The original new row before truncation
   * @param table0 The target table metadata
   */
  case class Update(truncatedNewRow: NormalizedRow, oldRow: NormalizedRow, validationMessages: Chain[ValidationMessage], untruncatedNewRow: NormalizedRow)(table0: ResolvedTable) extends RowSync {
    val table = table0
    val key = truncatedNewRow.key

    /**
     * Checks if this update would result in no changes.
     * 
     * Returns true if all non-omitted values are identical between
     * the old and new rows, indicating the update can be skipped.
     */
    lazy val isNoop: Boolean =
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

  /**
   * Represents a DELETE operation for an existing row.
   * 
   * @param oldRow The row to be deleted from the target database
   * @param validationMessages Any validation issues encountered
   * @param table0 The target table metadata
   */
  case class Delete(oldRow: NormalizedRow, validationMessages: Chain[ValidationMessage])(table0: ResolvedTable) extends RowSync {
    val table = table0
    val key = oldRow.key
  }

}

/**
 * Represents a database synchronization operation for a single row.
 * 
 * RowSync is the core abstraction for row-level database synchronization,
 * representing one of three operations: INSERT, UPDATE, or DELETE.
 * 
 * == Overview ==
 * 
 * RowSync encapsulates:
 * - The type of operation (insert/update/delete)
 * - The row data (including before/after states for updates)
 * - Validation messages for data quality issues
 * - SQL generation for executing the operation
 * 
 * == Data Validation ==
 * 
 * During synchronization, data is validated and potentially transformed:
 * - String values may be truncated to fit column sizes
 * - Validation messages are generated for data quality issues
 * - Truncation behavior is configurable per field
 * 
 * == SQL Generation ==
 * 
 * RowSync automatically generates the appropriate SQL for each operation:
 * {{{n * val sync: RowSync = // ... created by sync engine
 * sync.updateSql match {
 *   case Some(sql) => conn.update(sql)
 *   case None => // No-op update, skip
 * }
 * }}}
 * 
 * == Usage Example ==
 * 
 * RowSync instances are typically created by the synchronization engine:
 * {{{n * val syncJob = SyncJob[Customer](
 *   sourceTable = sourceConfig,
 *   targetTable = targetConfig
 * )
 * 
 * syncJob.run().foreach { rowSync =>
 *   rowSync match {
 *     case _: RowSync.Insert => println("Inserting row")
 *     case _: RowSync.Update => println("Updating row")
 *     case _: RowSync.Delete => println("Deleting row")
 *   }
 * }
 * }}}
 * 
 * @see [[ResolvedTable]] for table metadata
 * @see [[NormalizedRow]] for row data representation
 */
sealed trait RowSync {

  /**
   * The target table metadata for this operation.
   */
  val table: ResolvedTable
  
  /**
   * The primary key of the row being synchronized.
   */
  val key: NormalizedKey
  
  /**
   * Validation messages generated during synchronization.
   * 
   * These may include warnings about data truncation, type conversions,
   * or other data quality issues.
   */
  val validationMessages: Chain[ValidationMessage]

  /**
   * Generates SQL for this synchronization operation.
   * 
   * Returns None for no-op updates (where old and new values are identical).
   * For all other operations, returns the appropriate INSERT, UPDATE, or DELETE SQL.
   * 
   * @return Some(sql) for executable operations, None for no-ops
   */
  def updateSql: Option[SqlString] =
    this match {
      case u: RowSync.Update if u.isNoop =>
        None
      case _ =>
        RowSync.impl.syncToSql(this).toSome
    }

}
