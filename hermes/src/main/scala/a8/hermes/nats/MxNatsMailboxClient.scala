package a8.hermes.nats

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import a8.hermes.nats.NatsMailboxClient.MailboxKVData
import a8.shared.json.impl.JsonCodecs.given
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxNatsMailboxClient {
  
  trait MxMailboxKVData { self: MailboxKVData.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[MailboxKVData,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[MailboxKVData,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[MailboxKVData,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.adminKey)
          .addField(_.readerKey)
          .addField(_.address)
          .addField(_.created)
          .addField(_.lastActivity)
          .addField(_.purgeTimeoutInMillis)
          .addField(_.closeTimeoutInMillis)
          .addField(_.channels)
          .addField(_.isNamed)
      )
      .build
    
    
    given scala.CanEqual[MailboxKVData, MailboxKVData] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[MailboxKVData,parameters.type] =  {
      val constructors = Constructors[MailboxKVData](9, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val adminKey: CaseClassParm[MailboxKVData,String] = CaseClassParm[MailboxKVData,String]("adminKey", _.adminKey, (d,v) => d.copy(adminKey = v), None, 0)
      lazy val readerKey: CaseClassParm[MailboxKVData,String] = CaseClassParm[MailboxKVData,String]("readerKey", _.readerKey, (d,v) => d.copy(readerKey = v), None, 1)
      lazy val address: CaseClassParm[MailboxKVData,String] = CaseClassParm[MailboxKVData,String]("address", _.address, (d,v) => d.copy(address = v), None, 2)
      lazy val created: CaseClassParm[MailboxKVData,Long] = CaseClassParm[MailboxKVData,Long]("created", _.created, (d,v) => d.copy(created = v), None, 3)
      lazy val lastActivity: CaseClassParm[MailboxKVData,Long] = CaseClassParm[MailboxKVData,Long]("lastActivity", _.lastActivity, (d,v) => d.copy(lastActivity = v), None, 4)
      lazy val purgeTimeoutInMillis: CaseClassParm[MailboxKVData,Long] = CaseClassParm[MailboxKVData,Long]("purgeTimeoutInMillis", _.purgeTimeoutInMillis, (d,v) => d.copy(purgeTimeoutInMillis = v), None, 5)
      lazy val closeTimeoutInMillis: CaseClassParm[MailboxKVData,Long] = CaseClassParm[MailboxKVData,Long]("closeTimeoutInMillis", _.closeTimeoutInMillis, (d,v) => d.copy(closeTimeoutInMillis = v), None, 6)
      lazy val channels: CaseClassParm[MailboxKVData,List[String]] = CaseClassParm[MailboxKVData,List[String]]("channels", _.channels, (d,v) => d.copy(channels = v), Some(()=> List("rpc-inbox")), 7)
      lazy val isNamed: CaseClassParm[MailboxKVData,Boolean] = CaseClassParm[MailboxKVData,Boolean]("isNamed", _.isNamed, (d,v) => d.copy(isNamed = v), Some(()=> false), 8)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): MailboxKVData = {
        MailboxKVData(
          adminKey = values(0).asInstanceOf[String],
          readerKey = values(1).asInstanceOf[String],
          address = values(2).asInstanceOf[String],
          created = values(3).asInstanceOf[Long],
          lastActivity = values(4).asInstanceOf[Long],
          purgeTimeoutInMillis = values(5).asInstanceOf[Long],
          closeTimeoutInMillis = values(6).asInstanceOf[Long],
          channels = values(7).asInstanceOf[List[String]],
          isNamed = values(8).asInstanceOf[Boolean],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): MailboxKVData = {
        val value =
          MailboxKVData(
            adminKey = values.next().asInstanceOf[String],
            readerKey = values.next().asInstanceOf[String],
            address = values.next().asInstanceOf[String],
            created = values.next().asInstanceOf[Long],
            lastActivity = values.next().asInstanceOf[Long],
            purgeTimeoutInMillis = values.next().asInstanceOf[Long],
            closeTimeoutInMillis = values.next().asInstanceOf[Long],
            channels = values.next().asInstanceOf[List[String]],
            isNamed = values.next().asInstanceOf[Boolean],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(adminKey: String, readerKey: String, address: String, created: Long, lastActivity: Long, purgeTimeoutInMillis: Long, closeTimeoutInMillis: Long, channels: List[String], isNamed: Boolean): MailboxKVData =
        MailboxKVData(adminKey, readerKey, address, created, lastActivity, purgeTimeoutInMillis, closeTimeoutInMillis, channels, isNamed)
    
    }
    
    
    lazy val typeName = "MailboxKVData"
  
  }
}
