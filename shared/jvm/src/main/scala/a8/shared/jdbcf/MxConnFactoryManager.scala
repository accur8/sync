package a8.shared.jdbcf

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxConnFactoryManager {
  
  trait MxPoolStats { self: PoolStats.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[PoolStats,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[PoolStats,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[PoolStats,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.totalConnections)
          .addField(_.activeConnections)
          .addField(_.idleConnections)
          .addField(_.threadsAwaitingConnection)
      )
      .build
    
    
    given scala.CanEqual[PoolStats, PoolStats] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[PoolStats,parameters.type] =  {
      val constructors = Constructors[PoolStats](4, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val totalConnections: CaseClassParm[PoolStats,Int] = CaseClassParm[PoolStats,Int]("totalConnections", _.totalConnections, (d,v) => d.copy(totalConnections = v), None, 0)
      lazy val activeConnections: CaseClassParm[PoolStats,Int] = CaseClassParm[PoolStats,Int]("activeConnections", _.activeConnections, (d,v) => d.copy(activeConnections = v), None, 1)
      lazy val idleConnections: CaseClassParm[PoolStats,Int] = CaseClassParm[PoolStats,Int]("idleConnections", _.idleConnections, (d,v) => d.copy(idleConnections = v), None, 2)
      lazy val threadsAwaitingConnection: CaseClassParm[PoolStats,Int] = CaseClassParm[PoolStats,Int]("threadsAwaitingConnection", _.threadsAwaitingConnection, (d,v) => d.copy(threadsAwaitingConnection = v), None, 3)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): PoolStats = {
        PoolStats(
          totalConnections = values(0).asInstanceOf[Int],
          activeConnections = values(1).asInstanceOf[Int],
          idleConnections = values(2).asInstanceOf[Int],
          threadsAwaitingConnection = values(3).asInstanceOf[Int],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): PoolStats = {
        val value =
          PoolStats(
            totalConnections = values.next().asInstanceOf[Int],
            activeConnections = values.next().asInstanceOf[Int],
            idleConnections = values.next().asInstanceOf[Int],
            threadsAwaitingConnection = values.next().asInstanceOf[Int],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(totalConnections: Int, activeConnections: Int, idleConnections: Int, threadsAwaitingConnection: Int): PoolStats =
        PoolStats(totalConnections, activeConnections, idleConnections, threadsAwaitingConnection)
    
    }
    
    
    lazy val typeName = "PoolStats"
  
  }
}
