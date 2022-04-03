package a8.sync

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}
import a8.shared.jdbcf.querydsl
import a8.shared.jdbcf.querydsl.QueryDsl

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.sync.http.RetryConfig

import scala.concurrent.duration.FiniteDuration

//====


object Mxhttp {
  
  trait MxRetryConfig {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[RetryConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[RetryConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[RetryConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.count)
          .addField(_.initialBackoff)
          .addField(_.maxBackoff)
      )
      .build
    
    implicit val catsEq: cats.Eq[RetryConfig] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[RetryConfig,parameters.type] =  {
      val constructors = Constructors[RetryConfig](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val count: CaseClassParm[RetryConfig,Int] = CaseClassParm[RetryConfig,Int]("count", _.count, (d,v) => d.copy(count = v), None, 0)
      lazy val initialBackoff: CaseClassParm[RetryConfig,FiniteDuration] = CaseClassParm[RetryConfig,FiniteDuration]("initialBackoff", _.initialBackoff, (d,v) => d.copy(initialBackoff = v), None, 1)
      lazy val maxBackoff: CaseClassParm[RetryConfig,FiniteDuration] = CaseClassParm[RetryConfig,FiniteDuration]("maxBackoff", _.maxBackoff, (d,v) => d.copy(maxBackoff = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): RetryConfig = {
        RetryConfig(
          count = values(0).asInstanceOf[Int],
          initialBackoff = values(1).asInstanceOf[FiniteDuration],
          maxBackoff = values(2).asInstanceOf[FiniteDuration],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): RetryConfig = {
        val value =
          RetryConfig(
            count = values.next().asInstanceOf[Int],
            initialBackoff = values.next().asInstanceOf[FiniteDuration],
            maxBackoff = values.next().asInstanceOf[FiniteDuration],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(count: Int, initialBackoff: FiniteDuration, maxBackoff: FiniteDuration): RetryConfig =
        RetryConfig(count, initialBackoff, maxBackoff)
    
    }
    
    
    lazy val typeName = "RetryConfig"
  
  }
}
