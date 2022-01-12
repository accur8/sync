package a8.sync.auditlog

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.sync.auditlog.AuditLogSync.TableSync
import sttp.model.Uri
import a8.shared.jdbcf.DatabaseConfig

//====


object MxConfig {
  
  trait MxConfig {
  
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Config,a8.shared.json.ast.JsObj] =
      a8.shared.json.JsonObjectCodecBuilder(generator)
        .addField(_.sourceDatabase)
        .addField(_.targetDatabase)
        .addField(_.tableSyncs)
        .build
    
    implicit val catsEq: cats.Eq[Config] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[Config,parameters.type] =  {
      val constructors = Constructors[Config](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val sourceDatabase: CaseClassParm[Config,DatabaseConfig] = CaseClassParm[Config,DatabaseConfig]("sourceDatabase", _.sourceDatabase, (d,v) => d.copy(sourceDatabase = v), None, 0)
      lazy val targetDatabase: CaseClassParm[Config,DatabaseConfig] = CaseClassParm[Config,DatabaseConfig]("targetDatabase", _.targetDatabase, (d,v) => d.copy(targetDatabase = v), None, 1)
      lazy val tableSyncs: CaseClassParm[Config,List[TableSync]] = CaseClassParm[Config,List[TableSync]]("tableSyncs", _.tableSyncs, (d,v) => d.copy(tableSyncs = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Config = {
        Config(
          sourceDatabase = values(0).asInstanceOf[DatabaseConfig],
          targetDatabase = values(1).asInstanceOf[DatabaseConfig],
          tableSyncs = values(2).asInstanceOf[List[TableSync]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Config = {
        val value =
          Config(
            sourceDatabase = values.next().asInstanceOf[DatabaseConfig],
            targetDatabase = values.next().asInstanceOf[DatabaseConfig],
            tableSyncs = values.next().asInstanceOf[List[TableSync]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(sourceDatabase: DatabaseConfig, targetDatabase: DatabaseConfig, tableSyncs: List[TableSync]): Config =
        Config(sourceDatabase, targetDatabase, tableSyncs)
    
    }
    
    
    lazy val typeName = "Config"
  
  }
}
