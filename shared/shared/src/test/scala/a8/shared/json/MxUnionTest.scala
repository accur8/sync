package a8.shared.json

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.json.UnionTest._
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxUnionTest {
  
  trait MxFoo1 {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Foo1,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Foo1,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Foo1,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.f0)
          .addField(_.f1)
      )
      .build
    
    
    given scala.CanEqual[Foo1, Foo1] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[Foo1,parameters.type] =  {
      val constructors = Constructors[Foo1](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val f0: CaseClassParm[Foo1,Int] = CaseClassParm[Foo1,Int]("f0", _.f0, (d,v) => d.copy(f0 = v), None, 0)
      lazy val f1: CaseClassParm[Foo1,String] = CaseClassParm[Foo1,String]("f1", _.f1, (d,v) => d.copy(f1 = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Foo1 = {
        Foo1(
          f0 = values(0).asInstanceOf[Int],
          f1 = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Foo1 = {
        val value =
          Foo1(
            f0 = values.next().asInstanceOf[Int],
            f1 = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(f0: Int, f1: String): Foo1 =
        Foo1(f0, f1)
    
    }
    
    
    lazy val typeName = "Foo1"
  
  }
}
