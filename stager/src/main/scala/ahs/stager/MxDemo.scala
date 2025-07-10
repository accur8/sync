package ahs.stager

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import a8.shared.jdbcf.DatabaseConfig.Password
import ahs.stager.Demo.DemoConfig
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxDemo {
  
  trait MxDemoConfig { self: DemoConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[DemoConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[DemoConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[DemoConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.vmDatabaseUser)
          .addField(_.vmDatabasePassword)
      )
      .build
    
    
    given scala.CanEqual[DemoConfig, DemoConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[DemoConfig,parameters.type] =  {
      val constructors = Constructors[DemoConfig](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val vmDatabaseUser: CaseClassParm[DemoConfig,String] = CaseClassParm[DemoConfig,String]("vmDatabaseUser", _.vmDatabaseUser, (d,v) => d.copy(vmDatabaseUser = v), None, 0)
      lazy val vmDatabasePassword: CaseClassParm[DemoConfig,Password] = CaseClassParm[DemoConfig,Password]("vmDatabasePassword", _.vmDatabasePassword, (d,v) => d.copy(vmDatabasePassword = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): DemoConfig = {
        DemoConfig(
          vmDatabaseUser = values(0).asInstanceOf[String],
          vmDatabasePassword = values(1).asInstanceOf[Password],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): DemoConfig = {
        val value =
          DemoConfig(
            vmDatabaseUser = values.next().asInstanceOf[String],
            vmDatabasePassword = values.next().asInstanceOf[Password],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(vmDatabaseUser: String, vmDatabasePassword: Password): DemoConfig =
        DemoConfig(vmDatabaseUser, vmDatabasePassword)
    
    }
    
    
    lazy val typeName = "DemoConfig"
  
  }
}
