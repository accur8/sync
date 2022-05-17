package a8.shared.jdbcf.querydsl

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}
import a8.shared.jdbcf.querydsl
import a8.shared.jdbcf.querydsl.QueryDsl

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.jdbcf.querydsl.QueryDslTest._

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
      
      lazy val container: Container.TableDsl = {
        val childJoin = QueryDsl.createJoin(join, "container", queryDsl.tableDsl, join=>new Container.TableDsl(join), Container.jdbcMapper) { (from,to) =>
          from.containerId === to.id
        }
        new Container.TableDsl(childJoin)
      }
    }
    
    val queryDsl = new QueryDsl[Widget, TableDsl, String](jdbcMapper, new TableDsl)
    
    def query[F[_]: cats.effect.Async](whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[F, Widget, TableDsl] =
      queryDsl.query(whereFn)
    
    def update[F[_]: cats.effect.Async](set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[F, TableDsl] =
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
        .addField(_.address)    
        .singlePrimaryKey(_.id)
        .buildKeyedTableMapper
    
    
    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
      val id = QueryDsl.field[String]("id", join)
      val count = QueryDsl.field[Long]("count", join)
      val name = QueryDsl.field[String]("name", join)
      val address = new Address.TableDsl(QueryDsl.ComponentJoin("address", join))
    
    }
    
    val queryDsl = new QueryDsl[Container, TableDsl, String](jdbcMapper, new TableDsl)
    
    def query[F[_]: cats.effect.Async](whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[F, Container, TableDsl] =
      queryDsl.query(whereFn)
    
    def update[F[_]: cats.effect.Async](set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[F, TableDsl] =
      queryDsl.update(set)
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Container,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Container,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Container,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.id)
          .addField(_.count)
          .addField(_.name)
          .addField(_.address)
      )
      .build
    
    implicit val catsEq: cats.Eq[Container] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Container,parameters.type] =  {
      val constructors = Constructors[Container](4, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val id: CaseClassParm[Container,String] = CaseClassParm[Container,String]("id", _.id, (d,v) => d.copy(id = v), None, 0)
      lazy val count: CaseClassParm[Container,Long] = CaseClassParm[Container,Long]("count", _.count, (d,v) => d.copy(count = v), None, 1)
      lazy val name: CaseClassParm[Container,String] = CaseClassParm[Container,String]("name", _.name, (d,v) => d.copy(name = v), None, 2)
      lazy val address: CaseClassParm[Container,Address] = CaseClassParm[Container,Address]("address", _.address, (d,v) => d.copy(address = v), None, 3)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Container = {
        Container(
          id = values(0).asInstanceOf[String],
          count = values(1).asInstanceOf[Long],
          name = values(2).asInstanceOf[String],
          address = values(3).asInstanceOf[Address],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Container = {
        val value =
          Container(
            id = values.next().asInstanceOf[String],
            count = values.next().asInstanceOf[Long],
            name = values.next().asInstanceOf[String],
            address = values.next().asInstanceOf[Address],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(id: String, count: Long, name: String, address: Address): Container =
        Container(id, count, name, address)
    
    }
    
    
    lazy val typeName = "Container"
  
  }
  
  
  
  
  trait MxAddress {
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.ComponentMapper[Address] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.line1)
        .addField(_.line2)
        .addField(_.city)
        .addField(_.state)
        .addField(_.zip)    
        .buildMapper
    
    
    class TableDsl(join: QueryDsl.Path) extends QueryDsl.Component[Address](join) {
      val line1 = QueryDsl.field[String]("line1", join)
      val line2 = QueryDsl.field[String]("line2", join)
      val city = QueryDsl.field[String]("city", join)
      val state = QueryDsl.field[String]("state", join)
      val zip = QueryDsl.field[String]("zip", join)
    
    }
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Address,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Address,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Address,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.line1)
          .addField(_.line2)
          .addField(_.city)
          .addField(_.state)
          .addField(_.zip)
      )
      .build
    
    implicit val catsEq: cats.Eq[Address] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Address,parameters.type] =  {
      val constructors = Constructors[Address](5, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val line1: CaseClassParm[Address,String] = CaseClassParm[Address,String]("line1", _.line1, (d,v) => d.copy(line1 = v), None, 0)
      lazy val line2: CaseClassParm[Address,String] = CaseClassParm[Address,String]("line2", _.line2, (d,v) => d.copy(line2 = v), None, 1)
      lazy val city: CaseClassParm[Address,String] = CaseClassParm[Address,String]("city", _.city, (d,v) => d.copy(city = v), None, 2)
      lazy val state: CaseClassParm[Address,String] = CaseClassParm[Address,String]("state", _.state, (d,v) => d.copy(state = v), None, 3)
      lazy val zip: CaseClassParm[Address,String] = CaseClassParm[Address,String]("zip", _.zip, (d,v) => d.copy(zip = v), None, 4)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Address = {
        Address(
          line1 = values(0).asInstanceOf[String],
          line2 = values(1).asInstanceOf[String],
          city = values(2).asInstanceOf[String],
          state = values(3).asInstanceOf[String],
          zip = values(4).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Address = {
        val value =
          Address(
            line1 = values.next().asInstanceOf[String],
            line2 = values.next().asInstanceOf[String],
            city = values.next().asInstanceOf[String],
            state = values.next().asInstanceOf[String],
            zip = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(line1: String, line2: String, city: String, state: String, zip: String): Address =
        Address(line1, line2, city, state, zip)
    
    }
    
    
    lazy val typeName = "Address"
  
  }
}
