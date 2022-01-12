package a8.sync.auditlog

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.jdbcf.{ColumnName, TableName}
import a8.sync.auditlog.AuditLog.Version
import a8.sync.auditlog.AuditLogSync.TableSync

//====


object MxAuditLogSync {
  
  trait MxTableSync {
  
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[TableSync,a8.shared.json.ast.JsObj] =
      a8.shared.json.JsonObjectCodecBuilder(generator)
        .addField(_.start)
        .addField(_.sourceTable)
        .addField(_.targetTable)
        .addField(_.primaryKey)
        .build
    
    implicit val catsEq: cats.Eq[TableSync] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[TableSync,parameters.type] =  {
      val constructors = Constructors[TableSync](4, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val start: CaseClassParm[TableSync,Version] = CaseClassParm[TableSync,Version]("start", _.start, (d,v) => d.copy(start = v), None, 0)
      lazy val sourceTable: CaseClassParm[TableSync,TableName] = CaseClassParm[TableSync,TableName]("sourceTable", _.sourceTable, (d,v) => d.copy(sourceTable = v), None, 1)
      lazy val targetTable: CaseClassParm[TableSync,TableName] = CaseClassParm[TableSync,TableName]("targetTable", _.targetTable, (d,v) => d.copy(targetTable = v), None, 2)
      lazy val primaryKey: CaseClassParm[TableSync,ColumnName] = CaseClassParm[TableSync,ColumnName]("primaryKey", _.primaryKey, (d,v) => d.copy(primaryKey = v), None, 3)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): TableSync = {
        TableSync(
          start = values(0).asInstanceOf[Version],
          sourceTable = values(1).asInstanceOf[TableName],
          targetTable = values(2).asInstanceOf[TableName],
          primaryKey = values(3).asInstanceOf[ColumnName],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): TableSync = {
        val value =
          TableSync(
            start = values.next().asInstanceOf[Version],
            sourceTable = values.next().asInstanceOf[TableName],
            targetTable = values.next().asInstanceOf[TableName],
            primaryKey = values.next().asInstanceOf[ColumnName],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(start: Version, sourceTable: TableName, targetTable: TableName, primaryKey: ColumnName): TableSync =
        TableSync(start, sourceTable, targetTable, primaryKey)
    
    }
    
    
    lazy val typeName = "TableSync"
  
  }
}
