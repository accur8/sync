package ahs.stager

import a8.shared.app.Ctx
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.SqlString.sqlStringContextImplicit
import a8.shared.jdbcf.{ColumnName, Conn, Row, RowWriter, SchemaName, SqlString, TableLocator, TableName}
import ahs.stager.model.TableNameResolver
import SqlString.*
import model.*


object CopyData {



  case class TableHandle(
    databaseId: DatabaseId,
    schema: SchemaName,
    tableName: TableName,
  )


  def runFullDataCopy(sourceVmInfo: Option[(VmDatabaseId,ClientId)], source: TableHandle, target: TableHandle)(using Ctx, Services) = {

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
        keyColumns.map(c => sql"${c.name}")
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

      sql"""select ${columns.map(_.name.asSqlFragment).mkSqlString(CommaSpace)} from ${tableName} order by ${orderByCols.mkSqlString(CommaSpace)}"""
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

    sourceVmInfo
      .foreach( (vmId,clientId) =>
        sourceConn
          .asInternal
          .prepare(sql"CALL ${vmId.programLibrary.keyword}/VMCALLPGM3 ('${clientId.toString.keyword}')")
          .unwrap
          .execute()
      )

    targetConn
      .update(sql"truncate ${target.tableName}")

    val batcher =
      targetConn
        .batcher[Row](insertSql)

    val results =
      batcher
        .execBatch(
          sourceConn
            .streamingQuery[Row](selectAllSql(source.tableName, forceUnicodeSortSeq = true))
            .stream
        )

  }



}
