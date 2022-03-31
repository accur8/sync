package a8.shared.json

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import JsonTest._
//====


object MxJsonTest {
  
  trait MxPerson {
  
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Person,a8.shared.json.ast.JsObj] =
      a8.shared.json.JsonObjectCodecBuilder(generator)
        .addField(_.first)
        .addField(_.last)
        .build
    
    implicit val catsEq: cats.Eq[Person] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Person,parameters.type] =  {
      val constructors = Constructors[Person](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val first: CaseClassParm[Person,String] = CaseClassParm[Person,String]("first", _.first, (d,v) => d.copy(first = v), None, 0)
      lazy val last: CaseClassParm[Person,String] = CaseClassParm[Person,String]("last", _.last, (d,v) => d.copy(last = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Person = {
        Person(
          first = values(0).asInstanceOf[String],
          last = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Person = {
        val value =
          Person(
            first = values.next().asInstanceOf[String],
            last = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(first: String, last: String): Person =
        Person(first, last)
    
    }
    
    
    lazy val typeName = "Person"
  
  }
  
  
  
  
  trait MxGroup {
  
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Group,a8.shared.json.ast.JsObj] =
      a8.shared.json.JsonObjectCodecBuilder(generator)
        .addField(_.members)
        .addField(_.name)
        .build
    
    implicit val catsEq: cats.Eq[Group] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Group,parameters.type] =  {
      val constructors = Constructors[Group](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val members: CaseClassParm[Group,Iterable[Person]] = CaseClassParm[Group,Iterable[Person]]("members", _.members, (d,v) => d.copy(members = v), None, 0)
      lazy val name: CaseClassParm[Group,String] = CaseClassParm[Group,String]("name", _.name, (d,v) => d.copy(name = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Group = {
        Group(
          members = values(0).asInstanceOf[Iterable[Person]],
          name = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Group = {
        val value =
          Group(
            members = values.next().asInstanceOf[Iterable[Person]],
            name = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(members: Iterable[Person], name: String): Group =
        Group(members, name)
    
    }
    
    
    lazy val typeName = "Group"
  
  }
  
  
  
  
  trait MxA {
  
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[A,a8.shared.json.ast.JsObj] =
      a8.shared.json.JsonObjectCodecBuilder(generator)
        .addField(_.value)
        .build
    
    implicit val catsEq: cats.Eq[A] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[A,parameters.type] =  {
      val constructors = Constructors[A](1, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val value: CaseClassParm[A,Option[String]] = CaseClassParm[A,Option[String]]("value", _.value, (d,v) => d.copy(value = v), None, 0)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): A = {
        A(
          value = values(0).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): A = {
        val value =
          A(
            value = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(value: Option[String]): A =
        A(value)
    
    }
    
    
    lazy val typeName = "A"
  
  }
}
