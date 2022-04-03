package a8.shared.mail

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====

//====


object MxMailConfig {
  
  trait MxMailConfig {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[MailConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[MailConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[MailConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.host)
          .addField(_.port)
          .addField(_.user)
          .addField(_.password)
          .addField(_.sslEnabled)
          .addField(_.startTlsEnabled)
          .addField(_.certCheckEnabled)
          .addField(_.debug)
      )
      .build
    
    implicit val catsEq: cats.Eq[MailConfig] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[MailConfig,parameters.type] =  {
      val constructors = Constructors[MailConfig](8, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val host: CaseClassParm[MailConfig,String] = CaseClassParm[MailConfig,String]("host", _.host, (d,v) => d.copy(host = v), None, 0)
      lazy val port: CaseClassParm[MailConfig,Option[Int]] = CaseClassParm[MailConfig,Option[Int]]("port", _.port, (d,v) => d.copy(port = v), Some(()=> None), 1)
      lazy val user: CaseClassParm[MailConfig,Option[String]] = CaseClassParm[MailConfig,Option[String]]("user", _.user, (d,v) => d.copy(user = v), Some(()=> None), 2)
      lazy val password: CaseClassParm[MailConfig,Option[String]] = CaseClassParm[MailConfig,Option[String]]("password", _.password, (d,v) => d.copy(password = v), Some(()=> None), 3)
      lazy val sslEnabled: CaseClassParm[MailConfig,Boolean] = CaseClassParm[MailConfig,Boolean]("sslEnabled", _.sslEnabled, (d,v) => d.copy(sslEnabled = v), Some(()=> false), 4)
      lazy val startTlsEnabled: CaseClassParm[MailConfig,Boolean] = CaseClassParm[MailConfig,Boolean]("startTlsEnabled", _.startTlsEnabled, (d,v) => d.copy(startTlsEnabled = v), Some(()=> true), 5)
      lazy val certCheckEnabled: CaseClassParm[MailConfig,Boolean] = CaseClassParm[MailConfig,Boolean]("certCheckEnabled", _.certCheckEnabled, (d,v) => d.copy(certCheckEnabled = v), Some(()=> true), 6)
      lazy val debug: CaseClassParm[MailConfig,Boolean] = CaseClassParm[MailConfig,Boolean]("debug", _.debug, (d,v) => d.copy(debug = v), Some(()=> false), 7)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): MailConfig = {
        MailConfig(
          host = values(0).asInstanceOf[String],
          port = values(1).asInstanceOf[Option[Int]],
          user = values(2).asInstanceOf[Option[String]],
          password = values(3).asInstanceOf[Option[String]],
          sslEnabled = values(4).asInstanceOf[Boolean],
          startTlsEnabled = values(5).asInstanceOf[Boolean],
          certCheckEnabled = values(6).asInstanceOf[Boolean],
          debug = values(7).asInstanceOf[Boolean],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): MailConfig = {
        val value =
          MailConfig(
            host = values.next().asInstanceOf[String],
            port = values.next().asInstanceOf[Option[Int]],
            user = values.next().asInstanceOf[Option[String]],
            password = values.next().asInstanceOf[Option[String]],
            sslEnabled = values.next().asInstanceOf[Boolean],
            startTlsEnabled = values.next().asInstanceOf[Boolean],
            certCheckEnabled = values.next().asInstanceOf[Boolean],
            debug = values.next().asInstanceOf[Boolean],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(host: String, port: Option[Int], user: Option[String], password: Option[String], sslEnabled: Boolean, startTlsEnabled: Boolean, certCheckEnabled: Boolean, debug: Boolean): MailConfig =
        MailConfig(host, port, user, password, sslEnabled, startTlsEnabled, certCheckEnabled, debug)
    
    }
    
    
    lazy val typeName = "MailConfig"
  
  }
}
