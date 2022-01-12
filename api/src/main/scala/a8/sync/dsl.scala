package a8.sync


import java.sql.{ResultSet}
import a8.sync.dsl.JsonPath
import fs2.Chunk
import org.typelevel.ci.CIString
import Imports._
import a8.sync.ResolvedTable.{ColumnMapper, DataType, ResolvedField}
import a8.sync.impl.{NormalizedKey, NormalizedRow, NormalizedTuple, NormalizedValue, SqlValue, resolveMapping}
import a8.shared.jdbcf.{ColumnName, Conn, Dialect, SchemaName, SqlString, TableName}
import a8.shared.json.DynamicJson
import a8.shared.json.ast.{JsNothing, JsVal}
import a8.sync.RowSync.ValidationMessage
import wvlet.log.LogLevel

import language.higherKinds

/**

 TODO actually take a sync and apply it to a database
 TODO we likely need a way to generate primary keys
 TODO we likely need a way to support side car tables

 DONE query the database to determine the primary keys
 DONE do some JNumber mangling to the same type(s) probly JDecimal so that keys and equals "just works (tm)"

 */
object dsl {

  case class Mapping(
    tables: Vector[Table],
    postDocumentAcquisitionFilter: DynamicJson=>Boolean = _ => true,
    defaultTruncationAction: TruncateAction = TruncateAction.Error,
  ) {
//    def resolveToSchema(schema: String)(implicit dialect: Dialect): ConnectionIO[ResolvedMapping] =
//      resolveMapping(schema, this)
  }

  sealed trait MappingResult

  object MappingResult {
    case class Success(document: DynamicJson, rowSyncs: Chain[RowSync], validationMessages: Chain[ValidationMessage]) extends MappingResult
    case class Error(document: DynamicJson, throwable: Throwable, validationMessages: Chain[ValidationMessage] = Chain.empty) extends MappingResult
    case class Skip(document: DynamicJson, validationMessages: Chain[ValidationMessage] = Chain.empty) extends MappingResult
  }

  case class MappingException(document: DynamicJson, throwable: Throwable) extends Exception(throwable)

  case class SkipMappingException(document: DynamicJson, reason: String) extends Exception(reason)

  case class ResolvedMapping(schema: SchemaName, tables: Vector[ResolvedTable], mapping: Mapping) {

    def processRootDocument[F[_] : Async](document: DynamicJson, conn: Conn[F]): F[MappingResult] = {
      mapping
        .postDocumentAcquisitionFilter(document)
        .toOption(
          tables
            .traverse(table => table.runSync(document, conn, mapping.defaultTruncationAction))
            .map(_.foldLeft(Chain.empty[RowSync])(_ ++ _))
            .map { rowSyncs =>
              val validationMessages = rowSyncs.flatMap(_.validationMessages)
              validationMessages.find(_._1 <= LogLevel.ERROR) match {
                case Some(firstError) =>
                  MappingResult.Error(document, new RuntimeException(s"Validation error found, the first error is -- ${firstError._2}"), validationMessages) : MappingResult
                case None =>
                  MappingResult.Success(document, rowSyncs, validationMessages) : MappingResult
              }
            }
            .handleError(th => MappingResult.Error(document, th, Chain.empty))
        )
        .getOrElse(Async[F].pure(MappingResult.Skip(document, Chain(LogLevel.INFO -> "skipped claim -- postProcessDocumentFilter returned false"))))
    }

  }

  case class Table(
    sourceRows: JsonPath,
    sourceRowsFilter: DynamicJson => Boolean = _ => true,
    targetTable: TableName,
    syncWhereClause: DynamicJson => SqlString,
    fields: Iterable[Field],
  ) {
    lazy val fieldsAsChunk = Chunk.array(fields.toArray)

    def addFields(moreFields: Iterable[Field]): Table =
      copy(fields = fields ++ moreFields)

  }

//  case class ColumnName(value: String) {
//    val unquoted = value.stripQuotes
//    lazy val ci = CIString(unquoted)
//  }

  sealed abstract class TruncateAction(val logLevel: Option[LogLevel]) extends enumeratum.EnumEntry
  object TruncateAction extends enumeratum.Enum[TruncateAction] {
    val values = findValues

    case object Error extends TruncateAction(Some(LogLevel.ERROR))
    case object Warn extends TruncateAction(Some(LogLevel.WARN))
    case object Auto extends TruncateAction(None)

  }

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
    def dataType(dataType: DataType): Field =
      copy(dataType = dataType)
    def primaryKey(keyOrdinal: Int): Field =
      copy(keyOrdinal = Some(keyOrdinal))
    def defaultValue(defaultValue: JsVal): Field =
      copy(defaultValue = defaultValue)
    def truncateAction(truncateAction: TruncateAction): Field =
      copy(truncateAction = Some(truncateAction))
    def omitOnInsertUpdate(omitOnInsertUpdate: DynamicJson => Boolean = _ => true): Field =
      copy(omitOnInsertUpdate = omitOnInsertUpdate)
  }

  case class JsonPath(
    getter: DynamicJson => DynamicJson
  )

  object JsonPath {

  }




}
