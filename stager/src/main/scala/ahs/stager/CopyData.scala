package ahs.stager

import a8.shared.app.Ctx
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.SqlString.sqlStringContextImplicit
import a8.shared.jdbcf.{ColumnName, Conn, PostgresBatcher, Row, RowWriter, SchemaName, SqlString, TableLocator, TableName}
import ahs.stager.model.TableNameResolver
import SqlString.*
import a8.common.logging.Logging
import a8.shared.ProgressCounter
import a8.shared.jdbcf.PostgresBatcher.{CopyValue, FieldDef, RecordBatcher}
import model.*

import java.sql.SQLException


object CopyData extends Logging {

  val batchSize = 10_000
//  val batchSize = 100

  case class TableHandle(
    databaseId: DatabaseId,
    schema: SchemaName,
    tableName: TableName,
  )


  def runFullDataCopy(vmId: VmDatabaseId, clientId: ClientId, vmMember: Option[VmMember], source: TableHandle, target: TableHandle)(using Ctx, Services) = {

    val sourceConn = summon[Services].connectionManager.conn(source.databaseId)
    val targetConn = summon[Services].connectionManager.conn(target.databaseId)

    val sourceTable = sourceConn.tableMetadata(TableLocator(schemaName = source.schema, tableName = source.tableName))

    val columns = sourceTable.columns
    val columnsByName = sourceTable.columnsByName

    val keyColumns =
      columns
        .flatMap( col =>
          col.jdbcPrimaryKey.map { pk =>
            pk.keyIndex -> col
          }
        )
        .sortBy(_._1)
        .map(_._2)

    def selectAllSql(tableName: TableName, forceUnicodeSortSeq: Boolean): SqlString = {
      import SqlString.CommaSpace
      import SqlString.QuestionMark

      val orderByCols: Seq[SqlString] =
        keyColumns.map(kc => sqlExpr(kc.name))
//      val orderByCols: Seq[SqlString] =
//        if ( forceUnicodeSortSeq )
//          correlationColumns
//            .map(columnsByName(_))
//            .map(col =>
//              if ( col.jdbcColumn.typeName == "char" ) {
//                sql"${col.name}"
////                sql"CAST({${col.name}} AS ${col.jdbcColumn.typeName.keyword}(${col.jdbcColumn.columnSize}) CCSID 1208)"
//              } else {
//                sql"${col.name}"
//              }
//            )
//        else
//          correlationColumns

      sql"""select ${columns.map(c => sqlExpr(c.name)).mkSqlString(CommaSpace)} from ${tableName} order by ${orderByCols.mkSqlString(CommaSpace)}"""
    }

    val insertSql = sql"insert into ${target.tableName} (${columns.map(_.name.transformForPostgres).mkSqlString(CommaSpace)}) values (${columns.map(_ => QuestionMark).mkSqlString(CommaSpace)})"

    given RowWriter[Row] =
      new RowWriter[Row] {

        override val parameterCount: Int = columns.size

        override def sqlString(a: Row): Option[SqlString] = None

        override def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName] =
          columns.map(c => ColumnName(s"${columnNamePrefix}${c.name}"))

        override def applyParameters(ps: java.sql.PreparedStatement, row: Row, startIndex: Int): Unit = {
          var index = startIndex
          columns.foreach { column =>
            ps.setObject(index+1, row.value(index))
            index += 1
          }
          index
        }

      }

//    sourceVmInfo
//      .foreach( (vmId,clientId,_) =>
//        sourceConn
//          .asInternal
//          .prepare(sql"CALL ${vmId.programLibrary.keyword}/VMCALLPGM3 ('${clientId.toString.keyword}')")
//          .unwrap
//          .execute()
//      )

    targetConn
      .update(sql"truncate ${target.tableName}")

    val sourceTableName =
      vmMember match {
        case Some(member) =>
          val vmAlias = TableName("QTEMP/" + target.tableName.value.toString)
          try {
            sourceConn
              .update(sql"""DROP ALIAS ${vmAlias}""")
          } catch {
            case _: SQLException =>
              () // noop
          }
          sourceConn
            .update(sql"""CREATE ALIAS ${vmAlias} FOR ${vmId.schemaName}/${source.tableName}(${member})""")
          vmAlias
        case None =>
          source.tableName
      }

    val totalRecordCount =
      sourceConn
        .query[Long](sql"select count(*) from ${sourceTableName}")
        .fetch

    val progressCounter =
      new ProgressCounter(
        ctx = s"Copying ${source.databaseId}/${source.schema}/${source.tableName} ${vmMember} to ${target.databaseId}/${target.schema}/${target.tableName}",
        total = totalRecordCount,
      )

    def anyRefToCopyValue(anyRef: AnyRef): CopyValue = {
      import CopyValue.given
      import CopyValue.*
      anyRef match {
        case null =>
          CopyValue.NULL
        case s: String =>
          s
        case b: java.lang.Boolean =>
          booleanToCopyValue(b)
        case i: java.lang.Integer =>
          intToCopyValue(i)
//        case l: java.lang.Long =>
//          l
//        case d: java.lang.Double =>
//          d
//        case f: java.lang.Float =>
//          f
        case d: java.sql.Date =>
          d
        case v: java.sql.Time =>
          v
        case v: java.sql.Timestamp =>
          v
        case bd: java.math.BigDecimal =>
          bd
        case _ =>
          throw new IllegalArgumentException(s"Unsupported type for copy: ${anyRef.getClass.getName}")
      }
    }

    def rowToMap(row: Row): Map[String, CopyValue] = {
      columns
        .map(c => c.name.value.toString.toLowerCase -> anyRefToCopyValue(row.value(c.jdbcColumn.indexInTable - 1)))
        .toMap
    }

    val fieldDefs: Iterable[FieldDef] =
      columns
        .map { column =>
          FieldDef(
            name = column.name.value.toString.toLowerCase,
            dataType = GenerateTableAndIndexDdl.postgresType(column.jdbcColumn),
            primaryKey = keyColumns.contains(column),
          )
        }

    val recordBatcher: RecordBatcher[Row,Unit] =
      RecordBatcher(
        rowToMap,
        fieldDefs,
        target.tableName.value.toString,
      )

    val postgresBatcher = PostgresBatcher[Row,Unit](recordBatcher)

    val iter =
      sourceConn
        .streamingQuery[Row](selectAllSql(sourceTableName, forceUnicodeSortSeq = true))
        .runIterator

    targetConn.asInternal.withInternalConn { jdbcConn =>
      while (iter.hasNext ) {
        logger.debug(s"reading up to ${batchSize} records")
        val rows = iter.take(batchSize).toBuffer
        logger.debug(s"read complete inserting ${rows.size} records")
        postgresBatcher.insert(rows)(using jdbcConn)
        progressCounter.incrementBy(rows.size)
      }
    }

  }


  def sqlExpr(c: ColumnName): SqlString = {
    if ( c.value.toString.toLowerCase == "ocuid" ) {
      sql"hex(ocuid)"
    } else {
      sql"${c}"
    }
  }


}
