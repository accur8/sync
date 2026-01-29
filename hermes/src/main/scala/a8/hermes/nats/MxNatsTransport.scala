package a8.hermes.nats

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import a8.hermes.nats.NatsTransport.Config

import _root_.scala.concurrent.duration.FiniteDuration
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxNatsTransport {
  
  trait MxConfig { self: Config.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Config,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Config,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Config,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.natsUrl)
          .addField(_.username)
          .addField(_.password)
          .addField(_.token)
          .addField(_.connectionName)
          .addField(_.maxReconnects)
          .addField(_.reconnectWait)
          .addField(_.connectionTimeout)
          .addField(_.appName)
      )
      .build
    
    
    given scala.CanEqual[Config, Config] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[Config,parameters.type] =  {
      val constructors = Constructors[Config](9, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val natsUrl: CaseClassParm[Config,String] = CaseClassParm[Config,String]("natsUrl", _.natsUrl, (d,v) => d.copy(natsUrl = v), None, 0)
      lazy val username: CaseClassParm[Config,Option[String]] = CaseClassParm[Config,Option[String]]("username", _.username, (d,v) => d.copy(username = v), Some(()=> None), 1)
      lazy val password: CaseClassParm[Config,Option[String]] = CaseClassParm[Config,Option[String]]("password", _.password, (d,v) => d.copy(password = v), Some(()=> None), 2)
      lazy val token: CaseClassParm[Config,Option[String]] = CaseClassParm[Config,Option[String]]("token", _.token, (d,v) => d.copy(token = v), Some(()=> None), 3)
      lazy val connectionName: CaseClassParm[Config,Option[String]] = CaseClassParm[Config,Option[String]]("connectionName", _.connectionName, (d,v) => d.copy(connectionName = v), Some(()=> None), 4)
      lazy val maxReconnects: CaseClassParm[Config,Int] = CaseClassParm[Config,Int]("maxReconnects", _.maxReconnects, (d,v) => d.copy(maxReconnects = v), Some(()=> 60), 5)
      lazy val reconnectWait: CaseClassParm[Config,FiniteDuration] = CaseClassParm[Config,FiniteDuration]("reconnectWait", _.reconnectWait, (d,v) => d.copy(reconnectWait = v), Some(()=> scala.concurrent.duration.FiniteDuration(2, "seconds")), 6)
      lazy val connectionTimeout: CaseClassParm[Config,FiniteDuration] = CaseClassParm[Config,FiniteDuration]("connectionTimeout", _.connectionTimeout, (d,v) => d.copy(connectionTimeout = v), Some(()=> scala.concurrent.duration.FiniteDuration(5, "seconds")), 7)
      lazy val appName: CaseClassParm[Config,Option[String]] = CaseClassParm[Config,Option[String]]("appName", _.appName, (d,v) => d.copy(appName = v), Some(()=> None), 8)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Config = {
        Config(
          natsUrl = values(0).asInstanceOf[String],
          username = values(1).asInstanceOf[Option[String]],
          password = values(2).asInstanceOf[Option[String]],
          token = values(3).asInstanceOf[Option[String]],
          connectionName = values(4).asInstanceOf[Option[String]],
          maxReconnects = values(5).asInstanceOf[Int],
          reconnectWait = values(6).asInstanceOf[FiniteDuration],
          connectionTimeout = values(7).asInstanceOf[FiniteDuration],
          appName = values(8).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Config = {
        val value =
          Config(
            natsUrl = values.next().asInstanceOf[String],
            username = values.next().asInstanceOf[Option[String]],
            password = values.next().asInstanceOf[Option[String]],
            token = values.next().asInstanceOf[Option[String]],
            connectionName = values.next().asInstanceOf[Option[String]],
            maxReconnects = values.next().asInstanceOf[Int],
            reconnectWait = values.next().asInstanceOf[FiniteDuration],
            connectionTimeout = values.next().asInstanceOf[FiniteDuration],
            appName = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(natsUrl: String, username: Option[String], password: Option[String], token: Option[String], connectionName: Option[String], maxReconnects: Int, reconnectWait: FiniteDuration, connectionTimeout: FiniteDuration, appName: Option[String]): Config =
        Config(natsUrl, username, password, token, connectionName, maxReconnects, reconnectWait, connectionTimeout, appName)
    
    }
    
    
    lazy val typeName = "Config"
  
  }
}
