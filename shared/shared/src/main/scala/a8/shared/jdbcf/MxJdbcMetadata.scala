package a8.shared.jdbcf

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcPrimaryKey, JdbcTable}
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxJdbcMetadata {
  
  trait MxJdbcPrimaryKey { self: JdbcPrimaryKey.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[JdbcPrimaryKey,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[JdbcPrimaryKey,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[JdbcPrimaryKey,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.resolvedTableName)
          .addField(_.columnName)
          .addField(_.keyIndex)
          .addField(_.primaryKeyName)
      )
      .build
    
    
    given scala.CanEqual[JdbcPrimaryKey, JdbcPrimaryKey] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[JdbcPrimaryKey,parameters.type] =  {
      val constructors = Constructors[JdbcPrimaryKey](4, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val resolvedTableName: CaseClassParm[JdbcPrimaryKey,ResolvedTableName] = CaseClassParm[JdbcPrimaryKey,ResolvedTableName]("resolvedTableName", _.resolvedTableName, (d,v) => d.copy(resolvedTableName = v), None, 0)
      lazy val columnName: CaseClassParm[JdbcPrimaryKey,ColumnName] = CaseClassParm[JdbcPrimaryKey,ColumnName]("columnName", _.columnName, (d,v) => d.copy(columnName = v), None, 1)
      lazy val keyIndex: CaseClassParm[JdbcPrimaryKey,Short] = CaseClassParm[JdbcPrimaryKey,Short]("keyIndex", _.keyIndex, (d,v) => d.copy(keyIndex = v), None, 2)
      lazy val primaryKeyName: CaseClassParm[JdbcPrimaryKey,Option[String]] = CaseClassParm[JdbcPrimaryKey,Option[String]]("primaryKeyName", _.primaryKeyName, (d,v) => d.copy(primaryKeyName = v), None, 3)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): JdbcPrimaryKey = {
        JdbcPrimaryKey(
          resolvedTableName = values(0).asInstanceOf[ResolvedTableName],
          columnName = values(1).asInstanceOf[ColumnName],
          keyIndex = values(2).asInstanceOf[Short],
          primaryKeyName = values(3).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): JdbcPrimaryKey = {
        val value =
          JdbcPrimaryKey(
            resolvedTableName = values.next().asInstanceOf[ResolvedTableName],
            columnName = values.next().asInstanceOf[ColumnName],
            keyIndex = values.next().asInstanceOf[Short],
            primaryKeyName = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(resolvedTableName: ResolvedTableName, columnName: ColumnName, keyIndex: Short, primaryKeyName: Option[String]): JdbcPrimaryKey =
        JdbcPrimaryKey(resolvedTableName, columnName, keyIndex, primaryKeyName)
    
    }
    
    
    lazy val typeName = "JdbcPrimaryKey"
  
  }
  
  
  
  
  trait MxJdbcColumn { self: JdbcColumn.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[JdbcColumn,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[JdbcColumn,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[JdbcColumn,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.resolvedTableName)
          .addField(_.columnName)
          .addField(_.dataType)
          .addField(_.typeName)
          .addField(_.columnSize)
          .addField(_.bufferLength)
          .addField(_.decimalDigits)
          .addField(_.nullable)
          .addField(_.remarks)
          .addField(_.defaultValue)
          .addField(_.indexInTable)
          .addField(_.isNullable)
          .addField(_.isAutoIncrement)
          .addField(_.alternativeNames)
      )
      .build
    
    
    given scala.CanEqual[JdbcColumn, JdbcColumn] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[JdbcColumn,parameters.type] =  {
      val constructors = Constructors[JdbcColumn](14, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val resolvedTableName: CaseClassParm[JdbcColumn,ResolvedTableName] = CaseClassParm[JdbcColumn,ResolvedTableName]("resolvedTableName", _.resolvedTableName, (d,v) => d.copy(resolvedTableName = v), None, 0)
      lazy val columnName: CaseClassParm[JdbcColumn,ColumnName] = CaseClassParm[JdbcColumn,ColumnName]("columnName", _.columnName, (d,v) => d.copy(columnName = v), None, 1)
      lazy val dataType: CaseClassParm[JdbcColumn,Int] = CaseClassParm[JdbcColumn,Int]("dataType", _.dataType, (d,v) => d.copy(dataType = v), None, 2)
      lazy val typeName: CaseClassParm[JdbcColumn,String] = CaseClassParm[JdbcColumn,String]("typeName", _.typeName, (d,v) => d.copy(typeName = v), None, 3)
      lazy val columnSize: CaseClassParm[JdbcColumn,Int] = CaseClassParm[JdbcColumn,Int]("columnSize", _.columnSize, (d,v) => d.copy(columnSize = v), None, 4)
      lazy val bufferLength: CaseClassParm[JdbcColumn,Option[Int]] = CaseClassParm[JdbcColumn,Option[Int]]("bufferLength", _.bufferLength, (d,v) => d.copy(bufferLength = v), None, 5)
      lazy val decimalDigits: CaseClassParm[JdbcColumn,Option[Int]] = CaseClassParm[JdbcColumn,Option[Int]]("decimalDigits", _.decimalDigits, (d,v) => d.copy(decimalDigits = v), None, 6)
      lazy val nullable: CaseClassParm[JdbcColumn,Int] = CaseClassParm[JdbcColumn,Int]("nullable", _.nullable, (d,v) => d.copy(nullable = v), None, 7)
      lazy val remarks: CaseClassParm[JdbcColumn,Option[String]] = CaseClassParm[JdbcColumn,Option[String]]("remarks", _.remarks, (d,v) => d.copy(remarks = v), None, 8)
      lazy val defaultValue: CaseClassParm[JdbcColumn,Option[String]] = CaseClassParm[JdbcColumn,Option[String]]("defaultValue", _.defaultValue, (d,v) => d.copy(defaultValue = v), None, 9)
      lazy val indexInTable: CaseClassParm[JdbcColumn,Int] = CaseClassParm[JdbcColumn,Int]("indexInTable", _.indexInTable, (d,v) => d.copy(indexInTable = v), None, 10)
      lazy val isNullable: CaseClassParm[JdbcColumn,Option[Boolean]] = CaseClassParm[JdbcColumn,Option[Boolean]]("isNullable", _.isNullable, (d,v) => d.copy(isNullable = v), None, 11)
      lazy val isAutoIncrement: CaseClassParm[JdbcColumn,Option[Boolean]] = CaseClassParm[JdbcColumn,Option[Boolean]]("isAutoIncrement", _.isAutoIncrement, (d,v) => d.copy(isAutoIncrement = v), None, 12)
      lazy val alternativeNames: CaseClassParm[JdbcColumn,Vector[ColumnName]] = CaseClassParm[JdbcColumn,Vector[ColumnName]]("alternativeNames", _.alternativeNames, (d,v) => d.copy(alternativeNames = v), Some(()=> Vector()), 13)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): JdbcColumn = {
        JdbcColumn(
          resolvedTableName = values(0).asInstanceOf[ResolvedTableName],
          columnName = values(1).asInstanceOf[ColumnName],
          dataType = values(2).asInstanceOf[Int],
          typeName = values(3).asInstanceOf[String],
          columnSize = values(4).asInstanceOf[Int],
          bufferLength = values(5).asInstanceOf[Option[Int]],
          decimalDigits = values(6).asInstanceOf[Option[Int]],
          nullable = values(7).asInstanceOf[Int],
          remarks = values(8).asInstanceOf[Option[String]],
          defaultValue = values(9).asInstanceOf[Option[String]],
          indexInTable = values(10).asInstanceOf[Int],
          isNullable = values(11).asInstanceOf[Option[Boolean]],
          isAutoIncrement = values(12).asInstanceOf[Option[Boolean]],
          alternativeNames = values(13).asInstanceOf[Vector[ColumnName]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): JdbcColumn = {
        val value =
          JdbcColumn(
            resolvedTableName = values.next().asInstanceOf[ResolvedTableName],
            columnName = values.next().asInstanceOf[ColumnName],
            dataType = values.next().asInstanceOf[Int],
            typeName = values.next().asInstanceOf[String],
            columnSize = values.next().asInstanceOf[Int],
            bufferLength = values.next().asInstanceOf[Option[Int]],
            decimalDigits = values.next().asInstanceOf[Option[Int]],
            nullable = values.next().asInstanceOf[Int],
            remarks = values.next().asInstanceOf[Option[String]],
            defaultValue = values.next().asInstanceOf[Option[String]],
            indexInTable = values.next().asInstanceOf[Int],
            isNullable = values.next().asInstanceOf[Option[Boolean]],
            isAutoIncrement = values.next().asInstanceOf[Option[Boolean]],
            alternativeNames = values.next().asInstanceOf[Vector[ColumnName]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(resolvedTableName: ResolvedTableName, columnName: ColumnName, dataType: Int, typeName: String, columnSize: Int, bufferLength: Option[Int], decimalDigits: Option[Int], nullable: Int, remarks: Option[String], defaultValue: Option[String], indexInTable: Int, isNullable: Option[Boolean], isAutoIncrement: Option[Boolean], alternativeNames: Vector[ColumnName]): JdbcColumn =
        JdbcColumn(resolvedTableName, columnName, dataType, typeName, columnSize, bufferLength, decimalDigits, nullable, remarks, defaultValue, indexInTable, isNullable, isAutoIncrement, alternativeNames)
    
    }
    
    
    lazy val typeName = "JdbcColumn"
  
  }
  
  
  
  
  trait MxJdbcTable { self: JdbcTable.type =>
  
    given scala.CanEqual[JdbcTable, JdbcTable] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[JdbcTable,parameters.type] =  {
      val constructors = Constructors[JdbcTable](7, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val catalog: CaseClassParm[JdbcTable,Option[CatalogName]] = CaseClassParm[JdbcTable,Option[CatalogName]]("catalog", _.catalog, (d,v) => d.copy(catalog = v), None, 0)
      lazy val schema: CaseClassParm[JdbcTable,Option[SchemaName]] = CaseClassParm[JdbcTable,Option[SchemaName]]("schema", _.schema, (d,v) => d.copy(schema = v), None, 1)
      lazy val tableName: CaseClassParm[JdbcTable,TableName] = CaseClassParm[JdbcTable,TableName]("tableName", _.tableName, (d,v) => d.copy(tableName = v), None, 2)
      lazy val tableType: CaseClassParm[JdbcTable,Option[String]] = CaseClassParm[JdbcTable,Option[String]]("tableType", _.tableType, (d,v) => d.copy(tableType = v), None, 3)
      lazy val remarks: CaseClassParm[JdbcTable,Option[String]] = CaseClassParm[JdbcTable,Option[String]]("remarks", _.remarks, (d,v) => d.copy(remarks = v), None, 4)
      lazy val rawRow: CaseClassParm[JdbcTable,Row] = CaseClassParm[JdbcTable,Row]("rawRow", _.rawRow, (d,v) => d.copy(rawRow = v), None, 5)
      lazy val origin: CaseClassParm[JdbcTable,Option[TableLocator]] = CaseClassParm[JdbcTable,Option[TableLocator]]("origin", _.origin, (d,v) => d.copy(origin = v), Some(()=> None), 6)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): JdbcTable = {
        JdbcTable(
          catalog = values(0).asInstanceOf[Option[CatalogName]],
          schema = values(1).asInstanceOf[Option[SchemaName]],
          tableName = values(2).asInstanceOf[TableName],
          tableType = values(3).asInstanceOf[Option[String]],
          remarks = values(4).asInstanceOf[Option[String]],
          rawRow = values(5).asInstanceOf[Row],
          origin = values(6).asInstanceOf[Option[TableLocator]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): JdbcTable = {
        val value =
          JdbcTable(
            catalog = values.next().asInstanceOf[Option[CatalogName]],
            schema = values.next().asInstanceOf[Option[SchemaName]],
            tableName = values.next().asInstanceOf[TableName],
            tableType = values.next().asInstanceOf[Option[String]],
            remarks = values.next().asInstanceOf[Option[String]],
            rawRow = values.next().asInstanceOf[Row],
            origin = values.next().asInstanceOf[Option[TableLocator]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(catalog: Option[CatalogName], schema: Option[SchemaName], tableName: TableName, tableType: Option[String], remarks: Option[String], rawRow: Row, origin: Option[TableLocator]): JdbcTable =
        JdbcTable(catalog, schema, tableName, tableType, remarks, rawRow, origin)
    
    }
    
    
    lazy val typeName = "JdbcTable"
  
  }
}
