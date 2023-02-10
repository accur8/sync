package a8.shared.jdbcf

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.jdbcf.MaterializedMapperDemo.BigBoo
import a8.shared.jdbcf.MaterializedMapperDemo.JsonCC
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}
import a8.shared.jdbcf


object MxMaterializedMapperDemo {
  
  trait MxJsonCC {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[JsonCC,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[JsonCC,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[JsonCC,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.foo)
          .addField(_.bar)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[JsonCC] = zio.prelude.Equal.default
    
    implicit val catsEq: cats.Eq[JsonCC] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[JsonCC,parameters.type] =  {
      val constructors = Constructors[JsonCC](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val foo: CaseClassParm[JsonCC,Int] = CaseClassParm[JsonCC,Int]("foo", _.foo, (d,v) => d.copy(foo = v), None, 0)
      lazy val bar: CaseClassParm[JsonCC,String] = CaseClassParm[JsonCC,String]("bar", _.bar, (d,v) => d.copy(bar = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): JsonCC = {
        JsonCC(
          foo = values(0).asInstanceOf[Int],
          bar = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): JsonCC = {
        val value =
          JsonCC(
            foo = values.next().asInstanceOf[Int],
            bar = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(foo: Int, bar: String): JsonCC =
        JsonCC(foo, bar)
    
    }
    
    
    lazy val typeName = "JsonCC"
  
  }
  
  
  
  
  trait MxBigBoo {
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.KeyedTableMapper[BigBoo,Int] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.grOup)
        .addField(_.name)    
        .tableName("BIGBOO")
        .singlePrimaryKey(_.grOup)
        .buildKeyedTableMapper
    
    
    class TableDsl(join: jdbcf.querydsl.QueryDsl.Join = jdbcf.querydsl.QueryDsl.RootJoin) {
      val grOup = jdbcf.querydsl.QueryDsl.field[Int]("grOup", join)
      val name = jdbcf.querydsl.QueryDsl.field[String]("name", join)
    
    }
    
    val queryDsl = new jdbcf.querydsl.QueryDsl[BigBoo, TableDsl, Int](jdbcMapper, new TableDsl)
    
    def query(whereFn: TableDsl => jdbcf.querydsl.QueryDsl.Condition): jdbcf.querydsl.SelectQuery[BigBoo, TableDsl] =
      queryDsl.query(whereFn)
    
    def update(set: TableDsl => Iterable[jdbcf.querydsl.UpdateQuery.Assignment[_]]): jdbcf.querydsl.UpdateQuery[TableDsl] =
      queryDsl.update(set)
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[BigBoo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[BigBoo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[BigBoo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.grOup)
          .addField(_.name)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[BigBoo] = zio.prelude.Equal.default
    
    implicit val catsEq: cats.Eq[BigBoo] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[BigBoo,parameters.type] =  {
      val constructors = Constructors[BigBoo](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val grOup: CaseClassParm[BigBoo,Int] = CaseClassParm[BigBoo,Int]("grOup", _.grOup, (d,v) => d.copy(grOup = v), None, 0)
      lazy val name: CaseClassParm[BigBoo,String] = CaseClassParm[BigBoo,String]("name", _.name, (d,v) => d.copy(name = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): BigBoo = {
        BigBoo(
          grOup = values(0).asInstanceOf[Int],
          name = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): BigBoo = {
        val value =
          BigBoo(
            grOup = values.next().asInstanceOf[Int],
            name = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(grOup: Int, name: String): BigBoo =
        BigBoo(grOup, name)
    
    }
    
    
    lazy val typeName = "BigBoo"
  
  }
}
