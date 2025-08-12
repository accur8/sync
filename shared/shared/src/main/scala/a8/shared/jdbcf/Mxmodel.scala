package a8.shared.jdbcf

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import _root_.scala // noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}
import a8.shared.jdbcf


object Mxmodel {
  
  trait MxResolvedTableName { self: ResolvedTableName.type =>
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.ComponentMapper[ResolvedTableName] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.catalog)
        .addField(_.schema)
        .addField(_.name)    
        .buildMapper
    
    
    class TableDsl(join: jdbcf.querydsl.QueryDsl.Path) extends jdbcf.querydsl.QueryDsl.Component[ResolvedTableName](join) {
      val catalog = jdbcf.querydsl.QueryDsl.field[Option[CatalogName]]("catalog", join)
      val schema = jdbcf.querydsl.QueryDsl.field[Option[SchemaName]]("schema", join)
      val name = jdbcf.querydsl.QueryDsl.field[TableName]("name", join)
    
    }
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[ResolvedTableName,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[ResolvedTableName,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[ResolvedTableName,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.catalog)
          .addField(_.schema)
          .addField(_.name)
      )
      .build
    
    
    given scala.CanEqual[ResolvedTableName, ResolvedTableName] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[ResolvedTableName,parameters.type] =  {
      val constructors = Constructors[ResolvedTableName](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val catalog: CaseClassParm[ResolvedTableName,Option[CatalogName]] = CaseClassParm[ResolvedTableName,Option[CatalogName]]("catalog", _.catalog, (d,v) => d.copy(catalog = v), None, 0)
      lazy val schema: CaseClassParm[ResolvedTableName,Option[SchemaName]] = CaseClassParm[ResolvedTableName,Option[SchemaName]]("schema", _.schema, (d,v) => d.copy(schema = v), None, 1)
      lazy val name: CaseClassParm[ResolvedTableName,TableName] = CaseClassParm[ResolvedTableName,TableName]("name", _.name, (d,v) => d.copy(name = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): ResolvedTableName = {
        ResolvedTableName(
          catalog = values(0).asInstanceOf[Option[CatalogName]],
          schema = values(1).asInstanceOf[Option[SchemaName]],
          name = values(2).asInstanceOf[TableName],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): ResolvedTableName = {
        val value =
          ResolvedTableName(
            catalog = values.next().asInstanceOf[Option[CatalogName]],
            schema = values.next().asInstanceOf[Option[SchemaName]],
            name = values.next().asInstanceOf[TableName],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(catalog: Option[CatalogName], schema: Option[SchemaName], name: TableName): ResolvedTableName =
        ResolvedTableName(catalog, schema, name)
    
    }
    
    
    lazy val typeName = "ResolvedTableName"
  
  }
}
