package a8.shared.jdbcf

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import sttp.model.Uri
import a8.shared.jdbcf.DatabaseConfig.{DatabaseId, Password}
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxDatabaseConfig {
  
  trait MxDatabaseConfig { self: DatabaseConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[DatabaseConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[DatabaseConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[DatabaseConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.id)
          .addField(_.url)
          .addField(_.user)
          .addField(_.password)
          .addField(_.minIdle)
          .addField(_.maxPoolSize)
          .addField(_.maxLifeTimeInSeconds)
          .addField(_.connectionTimeoutInSeconds)
          .addField(_.idleTimeoutInSeconds)
          .addField(_.autoCommit)
          .addField(_.driverClassName)
      )
      .build
    
    
    given scala.CanEqual[DatabaseConfig, DatabaseConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[DatabaseConfig,parameters.type] =  {
      val constructors = Constructors[DatabaseConfig](11, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val id: CaseClassParm[DatabaseConfig,DatabaseId] = CaseClassParm[DatabaseConfig,DatabaseId]("id", _.id, (d,v) => d.copy(id = v), None, 0)
      lazy val url: CaseClassParm[DatabaseConfig,Uri] = CaseClassParm[DatabaseConfig,Uri]("url", _.url, (d,v) => d.copy(url = v), None, 1)
      lazy val user: CaseClassParm[DatabaseConfig,String] = CaseClassParm[DatabaseConfig,String]("user", _.user, (d,v) => d.copy(user = v), None, 2)
      lazy val password: CaseClassParm[DatabaseConfig,Password] = CaseClassParm[DatabaseConfig,Password]("password", _.password, (d,v) => d.copy(password = v), None, 3)
      lazy val minIdle: CaseClassParm[DatabaseConfig,Int] = CaseClassParm[DatabaseConfig,Int]("minIdle", _.minIdle, (d,v) => d.copy(minIdle = v), Some(()=> 1), 4)
      lazy val maxPoolSize: CaseClassParm[DatabaseConfig,Int] = CaseClassParm[DatabaseConfig,Int]("maxPoolSize", _.maxPoolSize, (d,v) => d.copy(maxPoolSize = v), Some(()=> 50), 5)
      lazy val maxLifeTimeInSeconds: CaseClassParm[DatabaseConfig,Seconds] = CaseClassParm[DatabaseConfig,Seconds]("maxLifeTimeInSeconds", _.maxLifeTimeInSeconds, (d,v) => d.copy(maxLifeTimeInSeconds = v), Some(()=> twentyFourHoursInSeconds), 6)
      lazy val connectionTimeoutInSeconds: CaseClassParm[DatabaseConfig,Seconds] = CaseClassParm[DatabaseConfig,Seconds]("connectionTimeoutInSeconds", _.connectionTimeoutInSeconds, (d,v) => d.copy(connectionTimeoutInSeconds = v), Some(()=> oneMinuteSeconds), 7)
      lazy val idleTimeoutInSeconds: CaseClassParm[DatabaseConfig,Seconds] = CaseClassParm[DatabaseConfig,Seconds]("idleTimeoutInSeconds", _.idleTimeoutInSeconds, (d,v) => d.copy(idleTimeoutInSeconds = v), Some(()=> twentyFourHoursInSeconds), 8)
      lazy val autoCommit: CaseClassParm[DatabaseConfig,Boolean] = CaseClassParm[DatabaseConfig,Boolean]("autoCommit", _.autoCommit, (d,v) => d.copy(autoCommit = v), Some(()=> true), 9)
      lazy val driverClassName: CaseClassParm[DatabaseConfig,Option[String]] = CaseClassParm[DatabaseConfig,Option[String]]("driverClassName", _.driverClassName, (d,v) => d.copy(driverClassName = v), Some(()=> None), 10)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): DatabaseConfig = {
        DatabaseConfig(
          id = values(0).asInstanceOf[DatabaseId],
          url = values(1).asInstanceOf[Uri],
          user = values(2).asInstanceOf[String],
          password = values(3).asInstanceOf[Password],
          minIdle = values(4).asInstanceOf[Int],
          maxPoolSize = values(5).asInstanceOf[Int],
          maxLifeTimeInSeconds = values(6).asInstanceOf[Seconds],
          connectionTimeoutInSeconds = values(7).asInstanceOf[Seconds],
          idleTimeoutInSeconds = values(8).asInstanceOf[Seconds],
          autoCommit = values(9).asInstanceOf[Boolean],
          driverClassName = values(10).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): DatabaseConfig = {
        val value =
          DatabaseConfig(
            id = values.next().asInstanceOf[DatabaseId],
            url = values.next().asInstanceOf[Uri],
            user = values.next().asInstanceOf[String],
            password = values.next().asInstanceOf[Password],
            minIdle = values.next().asInstanceOf[Int],
            maxPoolSize = values.next().asInstanceOf[Int],
            maxLifeTimeInSeconds = values.next().asInstanceOf[Seconds],
            connectionTimeoutInSeconds = values.next().asInstanceOf[Seconds],
            idleTimeoutInSeconds = values.next().asInstanceOf[Seconds],
            autoCommit = values.next().asInstanceOf[Boolean],
            driverClassName = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(id: DatabaseId, url: Uri, user: String, password: Password, minIdle: Int, maxPoolSize: Int, maxLifeTimeInSeconds: Seconds, connectionTimeoutInSeconds: Seconds, idleTimeoutInSeconds: Seconds, autoCommit: Boolean, driverClassName: Option[String]): DatabaseConfig =
        DatabaseConfig(id, url, user, password, minIdle, maxPoolSize, maxLifeTimeInSeconds, connectionTimeoutInSeconds, idleTimeoutInSeconds, autoCommit, driverClassName)
    
    }
    
    
    lazy val typeName = "DatabaseConfig"
  
  }
}
