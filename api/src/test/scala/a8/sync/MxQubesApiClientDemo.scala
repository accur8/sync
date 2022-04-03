package a8.sync

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}
import a8.shared.jdbcf.querydsl
import a8.shared.jdbcf.querydsl.QueryDsl

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.sync.QubesApiClientDemo.UserGroup

//====


object MxQubesApiClientDemo {
  
  trait MxUserGroup {
  
    implicit lazy val qubesMapper: a8.sync.qubes.QubesKeyedMapper[UserGroup,String] =
      a8.sync.qubes.QubesMapperBuilder(generator)
        .addField(_.uid)
        .addField(_.name)    
        .cubeName("UserGroup")
        .appSpace("qubes_admin")
        .singlePrimaryKey(_.uid)
        .build
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[UserGroup,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[UserGroup,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[UserGroup,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.uid)
          .addField(_.name)
      )
      .build
    
    implicit val catsEq: cats.Eq[UserGroup] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[UserGroup,parameters.type] =  {
      val constructors = Constructors[UserGroup](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val uid: CaseClassParm[UserGroup,String] = CaseClassParm[UserGroup,String]("uid", _.uid, (d,v) => d.copy(uid = v), None, 0)
      lazy val name: CaseClassParm[UserGroup,String] = CaseClassParm[UserGroup,String]("name", _.name, (d,v) => d.copy(name = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): UserGroup = {
        UserGroup(
          uid = values(0).asInstanceOf[String],
          name = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): UserGroup = {
        val value =
          UserGroup(
            uid = values.next().asInstanceOf[String],
            name = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(uid: String, name: String): UserGroup =
        UserGroup(uid, name)
    
    }
    
    
    lazy val typeName = "UserGroup"
  
  }
}
