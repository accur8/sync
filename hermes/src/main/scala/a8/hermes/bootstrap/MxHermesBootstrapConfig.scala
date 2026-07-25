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
  
  trait MxHermesAppConfig { self: HermesAppConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[HermesAppConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[HermesAppConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[HermesAppConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.namedMailbox)
          .addField(_.appName)
          .addField(_.mailboxLifecycle)
      )
      .build
    
    
    given scala.CanEqual[HermesAppConfig, HermesAppConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[HermesAppConfig,parameters.type] =  {
      val constructors = Constructors[HermesAppConfig](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val namedMailbox: CaseClassParm[HermesAppConfig,Option[String]] = CaseClassParm[HermesAppConfig,Option[String]]("namedMailbox", _.namedMailbox, (d,v) => d.copy(namedMailbox = v), Some(()=> None), 0)
      lazy val appName: CaseClassParm[HermesAppConfig,Option[String]] = CaseClassParm[HermesAppConfig,Option[String]]("appName", _.appName, (d,v) => d.copy(appName = v), Some(()=> None), 1)
      lazy val mailboxLifecycle: CaseClassParm[HermesAppConfig,String] = CaseClassParm[HermesAppConfig,String]("mailboxLifecycle", _.mailboxLifecycle, (d,v) => d.copy(mailboxLifecycle = v), Some(()=> "short-lived-cli"), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): HermesAppConfig = {
        HermesAppConfig(
          namedMailbox = values(0).asInstanceOf[Option[String]],
          appName = values(1).asInstanceOf[Option[String]],
          mailboxLifecycle = values(2).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): HermesAppConfig = {
        val value =
          HermesAppConfig(
            namedMailbox = values.next().asInstanceOf[Option[String]],
            appName = values.next().asInstanceOf[Option[String]],
            mailboxLifecycle = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(namedMailbox: Option[String], appName: Option[String], mailboxLifecycle: String): HermesAppConfig =
        HermesAppConfig(namedMailbox, appName, mailboxLifecycle)
    
    }
    
    
    lazy val typeName = "HermesAppConfig"
  
  }
  
  
  
  
  trait MxNamedMailboxKeys { self: NamedMailboxKeys.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[NamedMailboxKeys,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[NamedMailboxKeys,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[NamedMailboxKeys,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.address)
          .addField(_.adminKey)
          .addField(_.readerKey)
      )
      .build
    
    
    given scala.CanEqual[NamedMailboxKeys, NamedMailboxKeys] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[NamedMailboxKeys,parameters.type] =  {
      val constructors = Constructors[NamedMailboxKeys](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val address: CaseClassParm[NamedMailboxKeys,String] = CaseClassParm[NamedMailboxKeys,String]("address", _.address, (d,v) => d.copy(address = v), None, 0)
      lazy val adminKey: CaseClassParm[NamedMailboxKeys,String] = CaseClassParm[NamedMailboxKeys,String]("adminKey", _.adminKey, (d,v) => d.copy(adminKey = v), None, 1)
      lazy val readerKey: CaseClassParm[NamedMailboxKeys,String] = CaseClassParm[NamedMailboxKeys,String]("readerKey", _.readerKey, (d,v) => d.copy(readerKey = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): NamedMailboxKeys = {
        NamedMailboxKeys(
          address = values(0).asInstanceOf[String],
          adminKey = values(1).asInstanceOf[String],
          readerKey = values(2).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): NamedMailboxKeys = {
        val value =
          NamedMailboxKeys(
            address = values.next().asInstanceOf[String],
            adminKey = values.next().asInstanceOf[String],
            readerKey = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(address: String, adminKey: String, readerKey: String): NamedMailboxKeys =
        NamedMailboxKeys(address, adminKey, readerKey)
    
    }
    
    
    lazy val typeName = "NamedMailboxKeys"
  
  }
  
  
  
  
  trait MxHermesBootstrapConfig { self: HermesBootstrapConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[HermesBootstrapConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[HermesBootstrapConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[HermesBootstrapConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.natsUrl)
          .addField(_.httpUrl)
          .addField(_.namedMailboxKeys)
          .addField(_.sshKeyPath)
          .addField(_.authServiceMailbox)
          .addField(_.namedMailboxes)
          .addField(_.namingEnvironment)
          .addField(_.discoverySubject)
          .addField(_.autoRenewAuth)
      )
      .build
    
    
    given scala.CanEqual[HermesBootstrapConfig, HermesBootstrapConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[HermesBootstrapConfig,parameters.type] =  {
      val constructors = Constructors[HermesBootstrapConfig](9, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val natsUrl: CaseClassParm[HermesBootstrapConfig,String] = CaseClassParm[HermesBootstrapConfig,String]("natsUrl", _.natsUrl, (d,v) => d.copy(natsUrl = v), None, 0)
      lazy val httpUrl: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("httpUrl", _.httpUrl, (d,v) => d.copy(httpUrl = v), Some(()=> None), 1)
      lazy val namedMailboxKeys: CaseClassParm[HermesBootstrapConfig,Option[NamedMailboxKeys]] = CaseClassParm[HermesBootstrapConfig,Option[NamedMailboxKeys]]("namedMailboxKeys", _.namedMailboxKeys, (d,v) => d.copy(namedMailboxKeys = v), Some(()=> None), 2)
      lazy val sshKeyPath: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("sshKeyPath", _.sshKeyPath, (d,v) => d.copy(sshKeyPath = v), Some(()=> None), 3)
      lazy val authServiceMailbox: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("authServiceMailbox", _.authServiceMailbox, (d,v) => d.copy(authServiceMailbox = v), Some(()=> None), 4)
      lazy val namedMailboxes: CaseClassParm[HermesBootstrapConfig,Map[String,String]] = CaseClassParm[HermesBootstrapConfig,Map[String,String]]("namedMailboxes", _.namedMailboxes, (d,v) => d.copy(namedMailboxes = v), Some(()=> Map.empty), 5)
      lazy val namingEnvironment: CaseClassParm[HermesBootstrapConfig,Option[String]] = CaseClassParm[HermesBootstrapConfig,Option[String]]("namingEnvironment", _.namingEnvironment, (d,v) => d.copy(namingEnvironment = v), Some(()=> None), 6)
      lazy val discoverySubject: CaseClassParm[HermesBootstrapConfig,String] = CaseClassParm[HermesBootstrapConfig,String]("discoverySubject", _.discoverySubject, (d,v) => d.copy(discoverySubject = v), Some(()=> "continuum.discovery"), 7)
      lazy val autoRenewAuth: CaseClassParm[HermesBootstrapConfig,Boolean] = CaseClassParm[HermesBootstrapConfig,Boolean]("autoRenewAuth", _.autoRenewAuth, (d,v) => d.copy(autoRenewAuth = v), Some(()=> true), 8)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): HermesBootstrapConfig = {
        HermesBootstrapConfig(
          natsUrl = values(0).asInstanceOf[String],
          httpUrl = values(1).asInstanceOf[Option[String]],
          namedMailboxKeys = values(2).asInstanceOf[Option[NamedMailboxKeys]],
          sshKeyPath = values(3).asInstanceOf[Option[String]],
          authServiceMailbox = values(4).asInstanceOf[Option[String]],
          namedMailboxes = values(5).asInstanceOf[Map[String,String]],
          namingEnvironment = values(6).asInstanceOf[Option[String]],
          discoverySubject = values(7).asInstanceOf[String],
          autoRenewAuth = values(8).asInstanceOf[Boolean],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): HermesBootstrapConfig = {
        val value =
          HermesBootstrapConfig(
            natsUrl = values.next().asInstanceOf[String],
            httpUrl = values.next().asInstanceOf[Option[String]],
            namedMailboxKeys = values.next().asInstanceOf[Option[NamedMailboxKeys]],
            sshKeyPath = values.next().asInstanceOf[Option[String]],
            authServiceMailbox = values.next().asInstanceOf[Option[String]],
            namedMailboxes = values.next().asInstanceOf[Map[String,String]],
            namingEnvironment = values.next().asInstanceOf[Option[String]],
            discoverySubject = values.next().asInstanceOf[String],
            autoRenewAuth = values.next().asInstanceOf[Boolean],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(natsUrl: String, httpUrl: Option[String], namedMailboxKeys: Option[NamedMailboxKeys], sshKeyPath: Option[String], authServiceMailbox: Option[String], namedMailboxes: Map[String,String], namingEnvironment: Option[String], discoverySubject: String, autoRenewAuth: Boolean): HermesBootstrapConfig =
        HermesBootstrapConfig(natsUrl, httpUrl, namedMailboxKeys, sshKeyPath, authServiceMailbox, namedMailboxes, namingEnvironment, discoverySubject, autoRenewAuth)
    
    }
    
    
    lazy val typeName = "HermesBootstrapConfig"
  
  }
}
