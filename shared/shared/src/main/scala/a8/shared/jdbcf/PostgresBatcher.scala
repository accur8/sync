package a8.shared.jdbcf

import a8.shared.SharedImports.Logging
import a8.shared.jdbcf.PostgresBatcher.RecordBatcher
import a8.sync.qubes.QubesMapper

import java.io.StringReader
import java.util.UUID


object PostgresBatcher {

  object CopyValue {
    val NULL = CopyRaw("")

    implicit def dateToCopyValue(d: java.sql.Date): CopyValue =
      stringToCopyValue(d.toString)

    implicit def timeToCopyValue(d: java.sql.Time): CopyValue =
      stringToCopyValue(d.toString)

    implicit def timestampToCopyValue(d: java.sql.Timestamp): CopyValue =
      stringToCopyValue(d.toString)

    implicit def bigDecimalToCopyValue(v: java.math.BigDecimal): CopyValue =
      CopyRaw(v.toString)

    implicit def booleanToCopyValue(b: Boolean): CopyValue = CopyRaw(b.toString)
    implicit def intToCopyValue(i: Int): CopyValue = CopyRaw(i.toString)
    implicit def stringToCopyValue(s: String): CopyValue = CopyQuoted(s)
    implicit def optionStringToCopyValue(o: Option[String]): CopyValue =
      o.map(s => CopyQuoted(s)).getOrElse(NULL)
  }
  sealed trait CopyValue {

  }
  case class CopyQuoted(value: String) extends CopyValue
  case class CopyRaw(value: String) extends CopyValue

  case class RecordBatcher[A, PK](
    recordToMapFn: A => Map[String, CopyValue],
    fields: Iterable[FieldDef],
    tableName: String,
  ) {
    lazy val primaryKeyName =
      fields
        .find(_.primaryKey)
        .map(_.name)
        .get
    def tempTableName = {
      val uid = UUID.randomUUID().toString.replace("-", "")
      val tempTable = s"z_temp_${tableName}_${uid}"
      tempTable
    }
    def createTempTableSql(tempTableName: String) = {
      val createTempTableSql =
        s"""
           |CREATE TEMP TABLE ${tempTableName} (
           |  ${fields.map(_.createTableDdl).mkString(",")}
           |)
           |""".stripMargin
      //      val createTempTableSql =
      //        s"""
      //           |CREATE TABLE ${tempTableName} (
      //           |  ${fields.map(_.createTableDdl).mkString(",")}
      //           |)
      //           |""".stripMargin
      createTempTableSql
    }

    lazy val nonKeyFields =
      fields
        .filterNot(_.primaryKey)

    def writeCsvLine(record: A, sb: StringBuilder): Unit = {
      val map = recordToMapFn(record)
      var first = true
      fields
        .foreach { fieldDef =>
          if ( first ) {
            first = false
          } else {
            sb.append(",")
          }
          val value = map(fieldDef.name)
          value match {
            case CopyQuoted(s) =>
              sb.append('"')
              s.foreach(ch =>
                if (ch == '"' || ch == '\\') {
                  sb.append("\\")
                  //                } else if ( ch == '\n' ) {
                  //                  sb.append("\\n")
                  //                } else if (ch == '\r') {
                  //                  sb.append("\\r")
                  //                } else if (ch == '\t') {
                  //                  sb.append("\\t")
                } else {
                  sb.append(ch)
                }
              )
              sb.append('"')
            case CopyRaw(s) =>
              sb.append(s)
          }
        }
      sb.append("\n")
    }

  }

  case class FieldDef(
    name: String,
    dataType: String,
    primaryKey: Boolean = false,
  ) {
    lazy val quotedName = {
      val q = '"'.toString
      q + name + q
    }
    def createTableDdl: String = {
      val q = '"'.toString
      s"${q}${quotedName}${q} ${dataType}${if (primaryKey) " PRIMARY KEY" else ""}"
    }
  }

//  lazy val dataVirtTableBatcher =
//    RecordBatcher[DataVirtTable, String](
//      recordToMapFn = { record =>
//        Map[String,CopyValue](
//          "uid" -> record.uid,
//          "name" -> record.name,
//          "sqlTableName" -> record.sqlTableName,
//          "passthruColumns" -> record.passthruColumns,
//          "workspaceUid" -> record.workspaceUid.value,
//          "visible" -> record.visible,
//          "extraConfig" -> record.extraConfig.prettyJson,
//        )
//      },
//      fields = Seq(
//        FieldDef("uid", "VARCHAR(32)", true),
//        FieldDef("name", "VARCHAR(255)"),
//        FieldDef("sqlTableName", "VARCHAR(32)"),
//        FieldDef("passthruColumns", "BOOLEAN"),
//        FieldDef("workspaceUid", "VARCHAR(32)"),
//        FieldDef("visible", "BOOLEAN"),
//        FieldDef("extraConfig", "JSONB"),
//      ),
//    )
//
//  lazy val dataVirtJoinBatcher =
//    RecordBatcher[DataVirtJoin, String](
//      recordToMapFn = { record =>
//        Map(
//          "uid" -> record.uid,
//          "leftTableUid" -> record.leftTableUid,
//          "leftName" -> record.leftName,
//          "rightName" -> record.rightName,
//          "rightTableUid" -> record.rightTableUid,
//          "joinType" -> record.joinType,
//          "visible" -> record.visible,
//          "expr" -> record.expr,
//          "extraConfig" -> record.extraConfig.prettyJson,
//        )
//      },
//      fields = Seq(
//        FieldDef("uid", "VARCHAR(32)", true),
//        FieldDef("leftTableUid", "VARCHAR(32)"),
//        FieldDef("leftName", "VARCHAR(255)"),
//        FieldDef("rightName", "VARCHAR(255)"),
//        FieldDef("rightTableUid", "VARCHAR(32)"),
//        FieldDef("joinType", "VARCHAR(32)"),
//        FieldDef("visible", "BOOLEAN"),
//        FieldDef("expr", "VARCHAR(255)"),
//        FieldDef("extraConfig", "JSONB"),
//      ),
//    )

}

case class PostgresBatcher[A,PK](
  recordBatcher: RecordBatcher[A, PK],
)
  extends Logging
{

  def insert(records: Iterable[A])(implicit conn: java.sql.Connection): Unit = {
    logger.debug(s"batch insert on ${recordBatcher.tableName} of ${records.size} records")
    rawCopy(records, recordBatcher.tableName)
    logger.debug(s"batch insert complete")
  }

  def update(records: Iterable[A])(implicit conn: java.sql.Connection): Unit = {
    logger.debug(s"batch update on ${recordBatcher.tableName} of ${records.size} records")
    val tempTableName = recordBatcher.tempTableName
    val createTempTableSql = recordBatcher.createTempTableSql(tempTableName)

    executeUpdate(createTempTableSql)

    rawCopy(records, tempTableName)

    val updateSql =
      s"""
         |UPDATE ${recordBatcher.tableName}
         |SET ${recordBatcher.nonKeyFields.map(_.quotedName).map(name => s"${name} = tmp.${name}").mkString(",")}
         |FROM ${tempTableName} tmp
         |WHERE tmp.${recordBatcher.primaryKeyName} = ${recordBatcher.tableName}.${recordBatcher.primaryKeyName}
         |""".stripMargin

    executeUpdate(updateSql)

  }


  private def rawCopy(records: Iterable[A], tableName: String)(implicit conn: java.sql.Connection): Unit = {

    if (records.isEmpty) {
      logger.debug("No records to copy, skipping.")
      return
    }

    val sb = new StringBuilder()
    records
      .foreach { row =>
        recordBatcher.writeCsvLine(row, sb)
      }

    val copyContent = sb.toString()

    val pgConn = conn.unwrap(classOf[org.postgresql.PGConnection])
    val copyInSql = s"COPY ${tableName} (${recordBatcher.fields.map(_.quotedName).mkString(",")}) FROM STDIN WITH (FORMAT csv, ESCAPE '\\')"
    logger.debug("rawCopy sql: " + copyInSql)
    val reader = new StringReader(copyContent)
    try {
      pgConn.getCopyAPI.copyIn(copyInSql, reader)
    } finally {
      reader.close()
    }

  }

  private def executeUpdate(updateSql: String)(implicit conn: java.sql.Connection): Unit = {
    logger.debug(s"running update sql -- \n${updateSql}")
    val st = conn.createStatement()
    try {
      st.executeUpdate(updateSql)
    } finally {
      st.close()
    }
    logger.debug("update sql complete")
  }

}
