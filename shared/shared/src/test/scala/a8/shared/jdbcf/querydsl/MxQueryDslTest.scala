package a8.shared.jdbcf.querydsl

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.jdbcf.querydsl.QueryDslTest.Widget
import a8.shared.jdbcf.querydsl.QueryDslTest.Container

//====


object MxQueryDslTest {
  
  trait MxWidget {
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.KeyedTableMapper[Widget,String] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.id)
        .addField(_.name)
        .addField(_.containerId)    
        .singlePrimaryKey(_.id)
        .buildKeyedTableMapper
    
    
    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
      val id = QueryDsl.field[String]("id", join)
      val name = QueryDsl.field[String]("name", join)
      val containerId = QueryDsl.field[String]("containerId", join)
      
      lazy val container: TableDsl = {
        val childJoin = QueryDsl.createJoin(join, "container", queryDsl.tableDsl, ()=>container, Container.jdbcMapper) { (from,to) =>
          from.containerId === to.id
        }
        new TableDsl(childJoin)
      }
    }
    
    val queryDsl = new QueryDsl[Widget, TableDsl](jdbcMapper, new TableDsl)
    
    def query[F[_]: cats.effect.Async](whereFn: TableDsl => QueryDsl.Condition): SelectQuery[F, Widget, TableDsl] =
      queryDsl.query(whereFn)
    
    def update[F[_]: cats.effect.Async](set: TableDsl => Iterable[UpdateQuery.Assignment[_]]): UpdateQuery[F, TableDsl] =
      queryDsl.update(set)
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Widget,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Widget,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Widget,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.id)
          .addField(_.name)
          .addField(_.containerId)
      )
      .build
    
    implicit val catsEq: cats.Eq[Widget] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Widget,parameters.type] =  {
      val constructors = Constructors[Widget](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val id: CaseClassParm[Widget,String] = CaseClassParm[Widget,String]("id", _.id, (d,v) => d.copy(id = v), None, 0)
      lazy val name: CaseClassParm[Widget,String] = CaseClassParm[Widget,String]("name", _.name, (d,v) => d.copy(name = v), None, 1)
      lazy val containerId: CaseClassParm[Widget,String] = CaseClassParm[Widget,String]("containerId", _.containerId, (d,v) => d.copy(containerId = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Widget = {
        Widget(
          id = values(0).asInstanceOf[String],
          name = values(1).asInstanceOf[String],
          containerId = values(2).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Widget = {
        val value =
          Widget(
            id = values.next().asInstanceOf[String],
            name = values.next().asInstanceOf[String],
            containerId = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(id: String, name: String, containerId: String): Widget =
        Widget(id, name, containerId)
    
    }
    
    
    lazy val typeName = "Widget"
  
  }
  
  
  
  
  trait MxContainer {
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.KeyedTableMapper[Container,String] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.id)
        .addField(_.count)
        .addField(_.name)    
        .singlePrimaryKey(_.id)
        .buildKeyedTableMapper
    
    
    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
      val id = QueryDsl.field[String]("id", join)
      val count = QueryDsl.field[Long]("count", join)
      val name = QueryDsl.field[String]("name", join)
    
    }
    
    val queryDsl = new QueryDsl[Container, TableDsl](jdbcMapper, new TableDsl)
    
    def query[F[_]: cats.effect.Async](whereFn: TableDsl => QueryDsl.Condition): SelectQuery[F, Container, TableDsl] =
      queryDsl.query(whereFn)
    
    def update[F[_]: cats.effect.Async](set: TableDsl => Iterable[UpdateQuery.Assignment[_]]): UpdateQuery[F, TableDsl] =
      queryDsl.update(set)
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Container,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Container,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Container,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.id)
          .addField(_.count)
          .addField(_.name)
      )
      .build
    
    implicit val catsEq: cats.Eq[Container] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Container,parameters.type] =  {
      val constructors = Constructors[Container](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val id: CaseClassParm[Container,String] = CaseClassParm[Container,String]("id", _.id, (d,v) => d.copy(id = v), None, 0)
      lazy val count: CaseClassParm[Container,Long] = CaseClassParm[Container,Long]("count", _.count, (d,v) => d.copy(count = v), None, 1)
      lazy val name: CaseClassParm[Container,String] = CaseClassParm[Container,String]("name", _.name, (d,v) => d.copy(name = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Container = {
        Container(
          id = values(0).asInstanceOf[String],
          count = values(1).asInstanceOf[Long],
          name = values(2).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Container = {
        val value =
          Container(
            id = values.next().asInstanceOf[String],
            count = values.next().asInstanceOf[Long],
            name = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(id: String, count: Long, name: String): Container =
        Container(id, count, name)
    
    }
    
    
    lazy val typeName = "Container"
  
  }
}
