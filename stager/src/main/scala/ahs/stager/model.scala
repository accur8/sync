package ahs.stager

import a8.shared.StringValue.CIStringValue
import a8.shared.jdbcf.{Conn, SchemaName, TableName}
import a8.shared.jdbcf.SqlString.sqlStringContextImplicit
import a8.shared.{CompanionGen, StringValue}
import a8.shared.jdbcf.mapper.{PK, SqlTable}
import ahs.stager.Mxmodel.{MxClientInfo, MxTableInfo}
import org.typelevel.ci.CIString
import a8.shared.SharedImports.*
import sttp.model.Uri

object model {

  object VmDatabaseId extends StringValue.CIStringValueCompanion[VmDatabaseId]
  case class VmDatabaseId(value: CIString) extends CIStringValue {

    require(value.toString.startsWith("00"), "value must start with '00'")

    lazy val schemaName = SchemaName(z"VMD${value.toString.toUpperCase}")

    def jdbcUrl = {
      val uriStr = s"jdbc:as400://${schemaName.value.toString};naming=system;libraries=${schemaName.value.toString};errors=full"
      val uri = Uri.unsafeParse(uriStr)
      val s = uri.toString
      s.toString
      uri
    }

  }

  object ClientId extends StringValue.CIStringValueCompanion[ClientId]
  case class ClientId(value: CIString) extends CIStringValue

  object DivX extends StringValue.CIStringValueCompanion[DivX]
  case class DivX(value: CIString) extends CIStringValue

  object ResolvedTableName extends StringValue.Companion[ResolvedTableName]
  case class ResolvedTableName(value: String) extends StringValue

  enum SyncType {
    case Timestamp
    case Full
  }

  case class Table(
    name: TableName,
    syncType: SyncType,
    timestampColumn: Option[String] = None,
    indexes: Iterable[Index] = Iterable.empty,
  )

  case class Index(
    nameSuffix: String,
    ddl: String,
  )

  object TableInfo extends MxTableInfo
  @SqlTable(name="O1PMBR")
  @CompanionGen(jdbcMapper = true, cats = false, jsonCodec = true)
  case class TableInfo(
    @PK okfil: TableName,
    okdiv: DivX,
//    okflg: String,
//    okflgb: String,
//    okfil1: String,
//    okdiv1: String,
//    okfil2: String,
//    okdiv2: String,
//    okfil3: String,
//    okdiv3: String,
  )

  object ClientInfo extends MxClientInfo
  @SqlTable(name="C1PDBR")
  @CompanionGen(jdbcMapper = true, cats = false, jsonCodec = true)
  case class ClientInfo(
    @PK cbdiv: ClientId,
    cbdiva: ClientId,
    cbdivb: ClientId,
    cbdivc: ClientId,
    cbdivd: ClientId,
    cbdive: ClientId,
    cbdivf: ClientId,
    cbdivg: ClientId,
    cbdivh: ClientId,
    cbdivi: ClientId,
    cbdivj: ClientId,
    cbdivk: ClientId,
    cbdivl: ClientId,
    cbdivm: ClientId,
    cbdivn: ClientId,
    cbdivo: ClientId,
    cbdivp: ClientId,
    cbdivq: ClientId,
    cbdivr: ClientId,
    cbdivs: ClientId,
    cbdivt: ClientId,
    cbdivu: ClientId,
    cbdivv: ClientId,
    cbdivw: ClientId,
    cbdivx: ClientId,
    cbdivy: ClientId,
    cbdivz: ClientId,
  ) {

    lazy val asJsObj = ClientInfo.jsonCodec.write(this)

    def memberName(divx: DivX): String = {
      val fieldName = "cb" + divx.value.toString.toLowerCase
      asJsObj.values(fieldName).unsafeAs[String]
    }

  }

  case class TableNameResolver(
    tableInfos: Iterable[TableInfo],
    clientInfos: Iterable[ClientInfo],
  ) {

    lazy val tableInfosByTableName: Map[TableName, TableInfo] =
      tableInfos
        .map(ti => ti.okfil -> ti)
        .toMap

    lazy val clientsById: Map[ClientId, ClientInfo] =
      clientInfos
        .map(ci => ci.cbdiv -> ci)
        .toMap

    def resolveTableName(table: Table, clientId: ClientId): ResolvedTableName = {

      tableInfosByTableName.get(table.name) match {
        case Some(tableInfo) =>
          val clientInfo = clientsById(clientId)
          val memberName = clientInfo.memberName(tableInfo.okdiv)
          ResolvedTableName(table.name.value.toString + "_" + memberName)
        case None =>
          ResolvedTableName(table.name.value.toString)
      }

    }

  }

  def loadTableNameResolver()(using conn: Conn): TableNameResolver = {
    val selectAll = sql"""1 = 1"""
    val tableInfos = conn.selectRows[TableInfo](selectAll)
    val clientInfos = conn.selectRows[ClientInfo](selectAll)
    TableNameResolver(
      tableInfos = tableInfos,
      clientInfos = clientInfos,
    )
  }

}
