package a8.hermes.core

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import a8.hermes.core.Mailbox.{AdminKey, LifecycleType, MailboxAddress, MailboxMessage, MailboxMetadata, ReaderKey}

import java.time.Instant
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxMailbox {
  
  trait MxMailboxMetadata { self: MailboxMetadata.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[MailboxMetadata,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[MailboxMetadata,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[MailboxMetadata,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.adminKey)
          .addField(_.readerKey)
          .addField(_.address)
          .addField(_.lifecycle)
          .addField(_.createdAt)
          .addField(_.expiresAt)
          .addField(_.lastAccessedAt)
      )
      .build
    
    
    given scala.CanEqual[MailboxMetadata, MailboxMetadata] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[MailboxMetadata,parameters.type] =  {
      val constructors = Constructors[MailboxMetadata](7, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val adminKey: CaseClassParm[MailboxMetadata,AdminKey] = CaseClassParm[MailboxMetadata,AdminKey]("adminKey", _.adminKey, (d,v) => d.copy(adminKey = v), None, 0)
      lazy val readerKey: CaseClassParm[MailboxMetadata,ReaderKey] = CaseClassParm[MailboxMetadata,ReaderKey]("readerKey", _.readerKey, (d,v) => d.copy(readerKey = v), None, 1)
      lazy val address: CaseClassParm[MailboxMetadata,MailboxAddress] = CaseClassParm[MailboxMetadata,MailboxAddress]("address", _.address, (d,v) => d.copy(address = v), None, 2)
      lazy val lifecycle: CaseClassParm[MailboxMetadata,LifecycleType] = CaseClassParm[MailboxMetadata,LifecycleType]("lifecycle", _.lifecycle, (d,v) => d.copy(lifecycle = v), None, 3)
      lazy val createdAt: CaseClassParm[MailboxMetadata,Instant] = CaseClassParm[MailboxMetadata,Instant]("createdAt", _.createdAt, (d,v) => d.copy(createdAt = v), None, 4)
      lazy val expiresAt: CaseClassParm[MailboxMetadata,Instant] = CaseClassParm[MailboxMetadata,Instant]("expiresAt", _.expiresAt, (d,v) => d.copy(expiresAt = v), None, 5)
      lazy val lastAccessedAt: CaseClassParm[MailboxMetadata,Instant] = CaseClassParm[MailboxMetadata,Instant]("lastAccessedAt", _.lastAccessedAt, (d,v) => d.copy(lastAccessedAt = v), None, 6)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): MailboxMetadata = {
        MailboxMetadata(
          adminKey = values(0).asInstanceOf[AdminKey],
          readerKey = values(1).asInstanceOf[ReaderKey],
          address = values(2).asInstanceOf[MailboxAddress],
          lifecycle = values(3).asInstanceOf[LifecycleType],
          createdAt = values(4).asInstanceOf[Instant],
          expiresAt = values(5).asInstanceOf[Instant],
          lastAccessedAt = values(6).asInstanceOf[Instant],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): MailboxMetadata = {
        val value =
          MailboxMetadata(
            adminKey = values.next().asInstanceOf[AdminKey],
            readerKey = values.next().asInstanceOf[ReaderKey],
            address = values.next().asInstanceOf[MailboxAddress],
            lifecycle = values.next().asInstanceOf[LifecycleType],
            createdAt = values.next().asInstanceOf[Instant],
            expiresAt = values.next().asInstanceOf[Instant],
            lastAccessedAt = values.next().asInstanceOf[Instant],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(adminKey: AdminKey, readerKey: ReaderKey, address: MailboxAddress, lifecycle: LifecycleType, createdAt: Instant, expiresAt: Instant, lastAccessedAt: Instant): MailboxMetadata =
        MailboxMetadata(adminKey, readerKey, address, lifecycle, createdAt, expiresAt, lastAccessedAt)
    
    }
    
    
    lazy val typeName = "MailboxMetadata"
  
  }
  
  
  
//
//  trait MxMailboxMessage { self: MailboxMessage.type =>
//
//    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[MailboxMessage,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[MailboxMessage,parameters.type] = builder
//
//    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[MailboxMessage,a8.shared.json.ast.JsObj] =
//      jsonCodecBuilder(
//        a8.shared.json.JsonObjectCodecBuilder(generator)
//          .addField(_.correlationId)
//          .addField(_.fromMailbox)
//          .addField(_.endpoint)
//          .addField(_.contentType)
//          .addField(_.payload)
//          .addField(_.metadata)
//      )
//      .build
//
//
//    given scala.CanEqual[MailboxMessage, MailboxMessage] = scala.CanEqual.derived
//
//
//
//    lazy val generator: Generator[MailboxMessage,parameters.type] =  {
//      val constructors = Constructors[MailboxMessage](6, unsafe.iterRawConstruct)
//      Generator(constructors, parameters)
//    }
//
//    object parameters {
//      lazy val correlationId: CaseClassParm[MailboxMessage,String] = CaseClassParm[MailboxMessage,String]("correlationId", _.correlationId, (d,v) => d.copy(correlationId = v), None, 0)
//      lazy val fromMailbox: CaseClassParm[MailboxMessage,MailboxAddress] = CaseClassParm[MailboxMessage,MailboxAddress]("fromMailbox", _.fromMailbox, (d,v) => d.copy(fromMailbox = v), None, 1)
//      lazy val endpoint: CaseClassParm[MailboxMessage,String] = CaseClassParm[MailboxMessage,String]("endpoint", _.endpoint, (d,v) => d.copy(endpoint = v), None, 2)
//      lazy val contentType: CaseClassParm[MailboxMessage,String] = CaseClassParm[MailboxMessage,String]("contentType", _.contentType, (d,v) => d.copy(contentType = v), None, 3)
//      lazy val payload: CaseClassParm[MailboxMessage,Array[Byte]] = CaseClassParm[MailboxMessage,Array[Byte]]("payload", _.payload, (d,v) => d.copy(payload = v), None, 4)
//      lazy val metadata: CaseClassParm[MailboxMessage,Map[String,String]] = CaseClassParm[MailboxMessage,Map[String,String]]("metadata", _.metadata, (d,v) => d.copy(metadata = v), Some(()=> Map.empty), 5)
//    }
//
//
//    object unsafe {
//
//      def rawConstruct(values: IndexedSeq[Any]): MailboxMessage = {
//        MailboxMessage(
//          correlationId = values(0).asInstanceOf[String],
//          fromMailbox = values(1).asInstanceOf[MailboxAddress],
//          endpoint = values(2).asInstanceOf[String],
//          contentType = values(3).asInstanceOf[String],
//          payload = values(4).asInstanceOf[Array[Byte]],
//          metadata = values(5).asInstanceOf[Map[String,String]],
//        )
//      }
//      def iterRawConstruct(values: Iterator[Any]): MailboxMessage = {
//        val value =
//          MailboxMessage(
//            correlationId = values.next().asInstanceOf[String],
//            fromMailbox = values.next().asInstanceOf[MailboxAddress],
//            endpoint = values.next().asInstanceOf[String],
//            contentType = values.next().asInstanceOf[String],
//            payload = values.next().asInstanceOf[Array[Byte]],
//            metadata = values.next().asInstanceOf[Map[String,String]],
//          )
//        if ( values.hasNext )
//           sys.error("")
//        value
//      }
//      def typedConstruct(correlationId: String, fromMailbox: MailboxAddress, endpoint: String, contentType: String, payload: Array[Byte], metadata: Map[String,String]): MailboxMessage =
//        MailboxMessage(correlationId, fromMailbox, endpoint, contentType, payload, metadata)
//
//    }
//
//
//    lazy val typeName = "MailboxMessage"
//
//  }
}
