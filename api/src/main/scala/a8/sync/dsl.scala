package a8.sync


import java.sql.ResultSet
import a8.sync.dsl.JsonPath
import org.typelevel.ci.CIString
import Imports.*
import a8.common.logging.Level
import a8.sync.ResolvedTable.{ColumnMapper, DataType, ResolvedField}
import a8.sync.impl.{NormalizedKey, NormalizedRow, NormalizedTuple, NormalizedValue, SqlValue, resolveMapping}
import a8.shared.jdbcf.{ColumnName, Conn, Dialect, SchemaName, SqlString, TableName}
import a8.shared.json.DynamicJson
import a8.shared.json.ast.{JsNothing, JsVal}
import a8.sync.RowSync.ValidationMessage
import cats.data.Chain

import zio.*

/**
 * Domain-specific language (DSL) for defining database synchronization mappings.
 * 
 * The dsl package provides a declarative API for configuring how JSON documents
 * are mapped to database tables for synchronization operations.
 * 
 * == Overview ==
 * 
 * The DSL allows you to:
 * - Map JSON paths to database columns
 * - Define primary keys and sync logic
 * - Configure data validation and truncation
 * - Handle complex JSON-to-relational transformations
 * 
 * == Example Usage ==
 * 
 * {{{n * val mapping = Mapping(
 *   tables = Vector(
 *     Table(
 *       sourceRows = JsonPath(_.customers),
 *       targetTable = TableName("customers"),
 *       syncWhereClause = doc => sql"WHERE tenant_id = \${doc.tenantId}",
 *       fields = List(
 *         Field(
 *           from = JsonPath(_.id),
 *           toColumnName = ColumnName("customer_id"),
 *           keyOrdinal = Some(1)
 *         ),
 *         Field(
 *           from = JsonPath(_.name),
 *           toColumnName = ColumnName("customer_name")
 *         )
 *       )
 *     )
 *   )
 * )
 * }}}
 */
object dsl {

  /**
   * Top-level configuration for JSON-to-database synchronization mapping.
   * 
   * A Mapping defines how JSON documents are transformed into database operations
   * across one or more tables.
   * 
   * @param tables The tables to synchronize data into
   * @param postDocumentAcquisitionFilter Filter to skip certain documents
   * @param defaultTruncationAction Default action for string truncation
   * 
   * @example
   * {{{n   * Mapping(
   *   tables = Vector(customerTable, orderTable),
   *   postDocumentAcquisitionFilter = doc => doc.status != "deleted",
   *   defaultTruncationAction = TruncateAction.Warn
   * )
   * }}}
   */
  case class Mapping(
    tables: Vector[Table],
    postDocumentAcquisitionFilter: DynamicJson=>Boolean = _ => true,
    defaultTruncationAction: TruncateAction = TruncateAction.Error,
  ) {
//    def resolveToSchema(schema: String)(implicit dialect: Dialect): ConnectionIO[ResolvedMapping] =
//      resolveMapping(schema, this)
  }

  /**
   * Result of processing a JSON document through a mapping.
   */
  sealed trait MappingResult

  object MappingResult {
    /**
     * Successful mapping resulting in synchronization operations.
     * 
     * @param document The source JSON document
     * @param rowSyncs The generated row synchronization operations
     * @param validationMessages Any warnings or info messages
     */
    case class Success(document: DynamicJson, rowSyncs: Chain[RowSync], validationMessages: Chain[ValidationMessage]) extends MappingResult
    
    /**
     * Mapping failed with an error.
     * 
     * @param document The source JSON document
     * @param throwable The error that occurred
     * @param validationMessages Any validation messages before the error
     */
    case class Error(document: DynamicJson, throwable: Throwable, validationMessages: Chain[ValidationMessage] = Chain.empty) extends MappingResult
    
    /**
     * Document was intentionally skipped based on filters.
     * 
     * @param document The source JSON document
     * @param validationMessages Reason for skipping
     */
    case class Skip(document: DynamicJson, validationMessages: Chain[ValidationMessage] = Chain.empty) extends MappingResult
  }

  case class MappingException(document: DynamicJson, throwable: Throwable) extends Exception(throwable)

  case class SkipMappingException(document: DynamicJson, reason: String) extends Exception(reason)

  case class ResolvedMapping(schema: SchemaName, tables: Vector[ResolvedTable], mapping: Mapping) {

    def processRootDocument(document: DynamicJson, conn: Conn): Task[MappingResult] = {
      mapping
        .postDocumentAcquisitionFilter(document)
        .toOption(
          tables
            .map(table => table.runSync(document, conn, mapping.defaultTruncationAction))
            .sequence
            .map(_.foldLeft(Chain.empty[RowSync])(_ ++ _))
            .map { rowSyncs =>
              val validationMessages = rowSyncs.flatMap(_.validationMessages)
              validationMessages.find(_._1 >= Level.Error) match {
                case Some(firstError) =>
                  MappingResult.Error(document, new RuntimeException(s"Validation error found, the first error is -- ${firstError._2}"), validationMessages) : MappingResult
                case None =>
                  MappingResult.Success(document, rowSyncs, validationMessages) : MappingResult
              }
            }
            .catchAll(th => ZIO.succeed(MappingResult.Error(document, th, Chain.empty)))
        )
        .getOrElse(ZIO.succeed(MappingResult.Skip(document, Chain(Level.Info -> "skipped claim -- postProcessDocumentFilter returned false"))))
    }

  }

  /**
   * Configuration for mapping JSON data to a database table.
   * 
   * Table defines how a portion of a JSON document maps to rows in a database table,
   * including field mappings, filtering, and sync logic.
   * 
   * @param sourceRows JSON path to the array of source rows
   * @param sourceRowsFilter Filter to exclude certain rows
   * @param targetTable The database table to sync to
   * @param syncWhereClause WHERE clause to limit sync scope
   * @param fields Field mappings from JSON to columns
   * 
   * @example
   * {{{n   * Table(
   *   sourceRows = JsonPath(_.data.customers),
   *   targetTable = TableName("customers"),
   *   syncWhereClause = doc => sql"WHERE org_id = \${doc.orgId}",
   *   fields = List(
   *     Field(JsonPath(_.id), ColumnName("id")).primaryKey(1),
   *     Field(JsonPath(_.name), ColumnName("name")),
   *     Field(JsonPath(_.email), ColumnName("email"))
   *   )
   * )
   * }}}
   */
  case class Table(
    sourceRows: JsonPath,
    sourceRowsFilter: DynamicJson => Boolean = _ => true,
    targetTable: TableName,
    syncWhereClause: DynamicJson => SqlString,
    fields: Iterable[Field],
  ) {
    lazy val fieldsAsChunk: Chunk[Field] = Chunk.fromArray(fields.toArray)

    def addFields(moreFields: Iterable[Field]): Table =
      copy(fields = fields ++ moreFields)

  }

//  case class ColumnName(value: String) {
//    val unquoted = value.stripQuotes
//    lazy val ci = CIString(unquoted)
//  }

  /**
   * Defines how string truncation is handled during data synchronization.
   * 
   * When string values exceed the target column's size limit, this enum
   * controls whether to truncate automatically, log warnings, or fail with errors.
   * 
   * @param logLevel The log level for truncation events (None means silent truncation)
   */
  sealed abstract class TruncateAction(val logLevel: Option[Level]) extends enumeratum.EnumEntry
  object TruncateAction extends enumeratum.Enum[TruncateAction] {
    val values = findValues

    /**
     * Fail synchronization with an error when truncation would occur.
     * 
     * Use this for critical data where truncation could cause data loss.
     */
    case object Error extends TruncateAction(Some(Level.Error))
    
    /**
     * Automatically truncate but log a warning message.
     * 
     * Good default for most cases - data is synchronized but issues are logged.
     */
    case object Warn extends TruncateAction(Some(Level.Warn))
    
    /**
     * Silently truncate without any logging.
     * 
     * Use when truncation is expected and acceptable (e.g., legacy system migrations).
     */
    case object Auto extends TruncateAction(None)

  }

  /**
   * Maps a JSON path to a database column.
   * 
   * Field defines how a single value in a JSON document maps to a database column,
   * including data type conversion, validation, and key constraints.
   * 
   * @param from JSON path to extract the value
   * @param toColumnName Target database column name
   * @param keyOrdinal Position in composite primary key (if applicable)
   * @param defaultValue Default value if JSON path is missing
   * @param dataType Data type conversion strategy
   * @param truncateAction How to handle string truncation
   * @param omitOnInsertUpdate Conditional logic to skip this field
   * 
   * @example
   * {{{n   * Field(
   *   from = JsonPath(_.customerId),
   *   toColumnName = ColumnName("customer_id")
   * ).primaryKey(1)
   *  .dataType(DataType.Long)
   *  .defaultValue(JsNull)
   * }}}
   */
  case class Field(
    from: JsonPath,
    toColumnName: ColumnName,
    keyOrdinal: Option[Int] = None,
//    nullable: Boolean = false,
    defaultValue: JsVal = JsNothing,
    dataType: DataType = DataType.ColumnDefault,
    truncateAction: Option[TruncateAction] = None,
    omitOnInsertUpdate: DynamicJson => Boolean = _ => false,
  ) {
    /** Sets the data type for this field */
    def dataType(dataType: DataType): Field =
      copy(dataType = dataType)
    
    /** Marks this field as part of the primary key */
    def primaryKey(keyOrdinal: Int): Field =
      copy(keyOrdinal = Some(keyOrdinal))
    
    /** Sets a default value for missing JSON paths */
    def defaultValue(defaultValue: JsVal): Field =
      copy(defaultValue = defaultValue)
    
    /** Configures truncation behavior for strings */
    def truncateAction(truncateAction: TruncateAction): Field =
      copy(truncateAction = Some(truncateAction))
    
    /** Sets a condition to omit this field from insert/update */
    def omitOnInsertUpdate(omitOnInsertUpdate: DynamicJson => Boolean = _ => true): Field =
      copy(omitOnInsertUpdate = omitOnInsertUpdate)
  }

  /**
   * Represents a path to extract data from a JSON document.
   * 
   * JsonPath wraps a function that navigates through a JSON structure
   * to extract a specific value.
   * 
   * @param getter Function to extract a value from JSON
   * 
   * @example
   * {{{n   * // Extract customer.address.city
   * JsonPath(_.customer.address.city)
   * 
   * // Extract array element
   * JsonPath(_.orders(0).total)
   * }}}
   */
  case class JsonPath(
    getter: DynamicJson => DynamicJson
  )

  object JsonPath {

  }




}
