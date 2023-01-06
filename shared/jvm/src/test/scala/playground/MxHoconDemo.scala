package playground

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import _root_.scala
import playground.HoconDemo.BigFoo // noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxHoconDemo {
  
  trait MxBigFoo {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[BigFoo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[BigFoo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[BigFoo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.a)
          .addField(_.b)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[BigFoo] = zio.prelude.Equal.default
    
    implicit val catsEq: cats.Eq[BigFoo] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[BigFoo,parameters.type] =  {
      val constructors = Constructors[BigFoo](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val a: CaseClassParm[BigFoo,Int] = CaseClassParm[BigFoo,Int]("a", _.a, (d,v) => d.copy(a = v), None, 0)
      lazy val b: CaseClassParm[BigFoo,Option[String]] = CaseClassParm[BigFoo,Option[String]]("b", _.b, (d,v) => d.copy(b = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): BigFoo = {
        BigFoo(
          a = values(0).asInstanceOf[Int],
          b = values(1).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): BigFoo = {
        val value =
          BigFoo(
            a = values.next().asInstanceOf[Int],
            b = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(a: Int, b: Option[String]): BigFoo =
        BigFoo(a, b)
    
    }
    
    
    lazy val typeName = "BigFoo"
  
  }
}
