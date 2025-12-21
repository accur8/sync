package a8.hermes.bootstrap

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxHermesBootstrapConfig {
  
  trait MxHermesBootstrapConfig { self: HermesBootstrapConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[HermesBootstrapConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[HermesBootstrapConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[HermesBootstrapConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.natsUrl)
          .addField(_.sshKeyPath)
          .addField(_.authServiceMailbox)
          .addField(_.namedMailboxes)
          .addField(_.discoverySubject)
          .addField(_.enableDynamicDiscovery)
          .addField(_.appName)
          .addField(_.autoRenewAuth)
      )
      .build
    
    
    given scala.CanEqual[HermesBootstrapConfig, HermesBootstrapConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[HermesBootstrapConfig,parameters.type] =  {
      val constructors = Constructors[HermesBootstrapConfig](8, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val natsUrl: CaseClassParm[HermesBootstrapConfig,String] = CaseClassParm[HermesBootstrapConfig,String]("natsUrl", _.natsUrl, (d,v) => d.copy(natsUrl = v), None, 0)
      lazy val sshKeyPath: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("sshKeyPath", _.sshKeyPath, (d,v) => d.copy(sshKeyPath = v), Some(()=> None), 1)
      lazy val authServiceMailbox: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("authServiceMailbox", _.authServiceMailbox, (d,v) => d.copy(authServiceMailbox = v), Some(()=> None), 2)
      lazy val namedMailboxes: CaseClassParm[HermesBootstrapConfig,Map[String,String]] = CaseClassParm[HermesBootstrapConfig,Map[String,String]]("namedMailboxes", _.namedMailboxes, (d,v) => d.copy(namedMailboxes = v), Some(()=> Map.empty), 3)
      lazy val discoverySubject: CaseClassParm[HermesBootstrapConfig,String] = CaseClassParm[HermesBootstrapConfig,String]("discoverySubject", _.discoverySubject, (d,v) => d.copy(discoverySubject = v), Some(()=> "nefario.discovery"), 4)
      lazy val enableDynamicDiscovery: CaseClassParm[HermesBootstrapConfig,Option[Boolean]] = CaseClassParm[HermesBootstrapConfig,Option[Boolean]]("enableDynamicDiscovery", _.enableDynamicDiscovery, (d,v) => d.copy(enableDynamicDiscovery = v), Some(()=> None), 5)
      lazy val appName: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("appName", _.appName, (d,v) => d.copy(appName = v), Some(()=> None), 6)
      lazy val autoRenewAuth: CaseClassParm[HermesBootstrapConfig,Boolean] = CaseClassParm[HermesBootstrapConfig,Boolean]("autoRenewAuth", _.autoRenewAuth, (d,v) => d.copy(autoRenewAuth = v), Some(()=> true), 7)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): HermesBootstrapConfig = {
        HermesBootstrapConfig(
          natsUrl = values(0).asInstanceOf[String],
          sshKeyPath = values(1).asInstanceOf[Option[String]],
          authServiceMailbox = values(2).asInstanceOf[Option[String]],
          namedMailboxes = values(3).asInstanceOf[Map[String,String]],
          discoverySubject = values(4).asInstanceOf[String],
          enableDynamicDiscovery = values(5).asInstanceOf[Option[Boolean]],
          appName = values(6).asInstanceOf[Option[String]],
          autoRenewAuth = values(7).asInstanceOf[Boolean],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): HermesBootstrapConfig = {
        val value =
          HermesBootstrapConfig(
            natsUrl = values.next().asInstanceOf[String],
            sshKeyPath = values.next().asInstanceOf[Option[String]],
            authServiceMailbox = values.next().asInstanceOf[Option[String]],
            namedMailboxes = values.next().asInstanceOf[Map[String,String]],
            discoverySubject = values.next().asInstanceOf[String],
            enableDynamicDiscovery = values.next().asInstanceOf[Option[Boolean]],
            appName = values.next().asInstanceOf[Option[String]],
            autoRenewAuth = values.next().asInstanceOf[Boolean],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(natsUrl: String, sshKeyPath: Option[String], authServiceMailbox: Option[String], namedMailboxes: Map[String,String], discoverySubject: String, enableDynamicDiscovery: Option[Boolean], appName: Option[String], autoRenewAuth: Boolean): HermesBootstrapConfig =
        HermesBootstrapConfig(natsUrl, sshKeyPath, authServiceMailbox, namedMailboxes, discoverySubject, enableDynamicDiscovery, appName, autoRenewAuth)
    
    }
    
    
    lazy val typeName = "HermesBootstrapConfig"
  
  }
}
