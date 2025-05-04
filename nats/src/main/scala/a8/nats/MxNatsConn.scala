package a8.nats

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import NatsConn.NatsConfig
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxNatsConn {
  
  trait MxNatsConfig {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[NatsConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[NatsConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[NatsConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.natsUrl)
          .addField(_.user)
          .addField(_.password)
          .addField(_.maxResponseHeadersSize)
          .addField(_.keyValueBucketName)
          .addField(_.subject)
      )
      .build
    
    
    given scala.CanEqual[NatsConfig, NatsConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[NatsConfig,parameters.type] =  {
      val constructors = Constructors[NatsConfig](6, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val natsUrl: CaseClassParm[NatsConfig,String] = CaseClassParm[NatsConfig,String]("natsUrl", _.natsUrl, (d,v) => d.copy(natsUrl = v), None, 0)
      lazy val user: CaseClassParm[NatsConfig,Option[String]] = CaseClassParm[NatsConfig,Option[String]]("user", _.user, (d,v) => d.copy(user = v), Some(()=> None), 1)
      lazy val password: CaseClassParm[NatsConfig,Option[String]] = CaseClassParm[NatsConfig,Option[String]]("password", _.password, (d,v) => d.copy(password = v), Some(()=> None), 2)
      lazy val maxResponseHeadersSize: CaseClassParm[NatsConfig,Int] = CaseClassParm[NatsConfig,Int]("maxResponseHeadersSize", _.maxResponseHeadersSize, (d,v) => d.copy(maxResponseHeadersSize = v), Some(()=> 20 * 1024), 3)
      lazy val keyValueBucketName: CaseClassParm[NatsConfig,String] = CaseClassParm[NatsConfig,String]("keyValueBucketName", _.keyValueBucketName, (d,v) => d.copy(keyValueBucketName = v), Some(()=> "CaddyMessageOverflow"), 4)
      lazy val subject: CaseClassParm[NatsConfig,String] = CaseClassParm[NatsConfig,String]("subject", _.subject, (d,v) => d.copy(subject = v), Some(()=> "events.http.request"), 5)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): NatsConfig = {
        NatsConfig(
          natsUrl = values(0).asInstanceOf[String],
          user = values(1).asInstanceOf[Option[String]],
          password = values(2).asInstanceOf[Option[String]],
          maxResponseHeadersSize = values(3).asInstanceOf[Int],
          keyValueBucketName = values(4).asInstanceOf[String],
          subject = values(5).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): NatsConfig = {
        val value =
          NatsConfig(
            natsUrl = values.next().asInstanceOf[String],
            user = values.next().asInstanceOf[Option[String]],
            password = values.next().asInstanceOf[Option[String]],
            maxResponseHeadersSize = values.next().asInstanceOf[Int],
            keyValueBucketName = values.next().asInstanceOf[String],
            subject = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(natsUrl: String, user: Option[String], password: Option[String], maxResponseHeadersSize: Int, keyValueBucketName: String, subject: String): NatsConfig =
        NatsConfig(natsUrl, user, password, maxResponseHeadersSize, keyValueBucketName, subject)
    
    }
    
    
    lazy val typeName = "NatsConfig"
  
  }
}
