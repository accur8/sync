package ahs.stager

import a8.shared.jdbcf.TableName
import ahs.stager.model.SyncType

object AhsData {

//  O1PMBR
//  C1PDBR
//  BLPCAR
//  BLPPAT
//  C1PLOS
//  M1PUSR
//  OLPCHG
//  SDPPRO

  val tables = Seq(
    model.Table(
      name = TableName("O1PMBR"),
      syncType = SyncType.Full,
    ),
    model.Table(
      name = TableName("C1PDBR"),
      syncType = SyncType.Full,
    ),
    model.Table(
      name = TableName("C1PLOS"),
      syncType = SyncType.Full,
    ),
    model.Table(
      name = TableName("M1PUSR"),
      syncType = SyncType.Full,
    ),
    model.Table(
      name = TableName("BLPCAR"),
      syncType = SyncType.Full,
      indexes = Seq(
        model.Index(
          nameSuffix = "div_car",
          ddl = """CREATE INDEX {{indexName}} ON {{tableName}} (BADIV, BACAR)""",
        ),
      ),
    ),
    model.Table(
      name = TableName("BLPPAT"),
      syncType = SyncType.Full,
      indexes = Seq(
        model.Index(
          nameSuffix = "pat",
          ddl = """CREATE INDEX {{indexName}} ON {{tableName}} (BDPAT)""",
        ),
      ),
    ),
    model.Table(
      name = TableName("SDPPRO"),
      syncType = SyncType.Full,
    ),
    model.Table(
      name = TableName("OLPCHG"),
      syncType = SyncType.Timestamp,
      timestampColumn = Some("OCJRNTSP"),
    ),
  )

}
