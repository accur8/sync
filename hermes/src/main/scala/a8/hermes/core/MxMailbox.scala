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
}
