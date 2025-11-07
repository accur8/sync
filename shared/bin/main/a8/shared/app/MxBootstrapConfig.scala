package a8.shared.app

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.common.logging.LoggingBootstrapConfig.LoggingBootstrapConfigDto
import a8.shared.app.BootstrapConfig.*
import scala.concurrent.duration.FiniteDuration
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxBootstrapConfig {
  
  trait MxLogLevelConfig { self: LogLevelConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[LogLevelConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[LogLevelConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[LogLevelConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.level)
      )
      .build
    
    
    given scala.CanEqual[LogLevelConfig, LogLevelConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[LogLevelConfig,parameters.type] =  {
      val constructors = Constructors[LogLevelConfig](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[LogLevelConfig,String] = CaseClassParm[LogLevelConfig,String]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val level: CaseClassParm[LogLevelConfig,String] = CaseClassParm[LogLevelConfig,String]("level", _.level, (d,v) => d.copy(level = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): LogLevelConfig = {
        LogLevelConfig(
          name = values(0).asInstanceOf[String],
          level = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): LogLevelConfig = {
        val value =
          LogLevelConfig(
            name = values.next().asInstanceOf[String],
            level = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: String, level: String): LogLevelConfig =
        LogLevelConfig(name, level)
    
    }
    
    
    lazy val typeName = "LogLevelConfig"
  
  }
}
