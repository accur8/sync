package a8.shared.app

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.app.BootstrapConfig._
import zio.Duration

import scala.concurrent.duration.FiniteDuration

//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxBootstrapConfig {
  
  trait MxLogLevelConfig {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[LogLevelConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[LogLevelConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[LogLevelConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.level)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[LogLevelConfig] = zio.prelude.Equal.default
    
    implicit val catsEq: cats.Eq[LogLevelConfig] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[LogLevelConfig,parameters.type] =  {
      val constructors = Constructors[LogLevelConfig](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[LogLevelConfig,String] = CaseClassParm[LogLevelConfig,String]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val level: CaseClassParm[LogLevelConfig,String] = CaseClassParm[LogLevelConfig,String]("level", _.level, (d,v) => d.copy(level = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): LogLevelConfig = {
        LogLevelConfig(
          name = values(0).asInstanceOf[String],
          level = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): LogLevelConfig = {
        val value =
          LogLevelConfig(
            name = values.next().asInstanceOf[String],
            level = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: String, level: String): LogLevelConfig =
        LogLevelConfig(name, level)
    
    }
    
    
    lazy val typeName = "LogLevelConfig"
  
  }
  
  
  
  
  trait MxBootstrapConfigDto {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[BootstrapConfigDto,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[BootstrapConfigDto,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[BootstrapConfigDto,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.source)
          .addField(_.appName)
          .addField(_.consoleLogging)
          .addField(_.colorConsole)
          .addField(_.fileLogging)
          .addField(_.logAppConfig)
          .addField(_.logsDir)
          .addField(_.tempDir)
          .addField(_.cacheDir)
          .addField(_.dataDir)
          .addField(_.defaultLogLevel)
          .addField(_.logLevels)
          .addField(_.configFilePollInterval)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[BootstrapConfigDto] = zio.prelude.Equal.default
    
    implicit val catsEq: cats.Eq[BootstrapConfigDto] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[BootstrapConfigDto,parameters.type] =  {
      val constructors = Constructors[BootstrapConfigDto](13, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val source: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("source", _.source, (d,v) => d.copy(source = v), Some(()=> None), 0)
      lazy val appName: CaseClassParm[BootstrapConfigDto,Option[AppName]] = CaseClassParm[BootstrapConfigDto,Option[AppName]]("appName", _.appName, (d,v) => d.copy(appName = v), Some(()=> None), 1)
      lazy val consoleLogging: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("consoleLogging", _.consoleLogging, (d,v) => d.copy(consoleLogging = v), Some(()=> None), 2)
      lazy val colorConsole: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("colorConsole", _.colorConsole, (d,v) => d.copy(colorConsole = v), Some(()=> None), 3)
      lazy val fileLogging: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("fileLogging", _.fileLogging, (d,v) => d.copy(fileLogging = v), Some(()=> None), 4)
      lazy val logAppConfig: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("logAppConfig", _.logAppConfig, (d,v) => d.copy(logAppConfig = v), Some(()=> None), 5)
      lazy val logsDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("logsDir", _.logsDir, (d,v) => d.copy(logsDir = v), Some(()=> None), 6)
      lazy val tempDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("tempDir", _.tempDir, (d,v) => d.copy(tempDir = v), Some(()=> None), 7)
      lazy val cacheDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("cacheDir", _.cacheDir, (d,v) => d.copy(cacheDir = v), Some(()=> None), 8)
      lazy val dataDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("dataDir", _.dataDir, (d,v) => d.copy(dataDir = v), Some(()=> None), 9)
      lazy val defaultLogLevel: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("defaultLogLevel", _.defaultLogLevel, (d,v) => d.copy(defaultLogLevel = v), Some(()=> None), 10)
      lazy val logLevels: CaseClassParm[BootstrapConfigDto,Vector[LogLevelConfig]] = CaseClassParm[BootstrapConfigDto,Vector[LogLevelConfig]]("logLevels", _.logLevels, (d,v) => d.copy(logLevels = v), Some(()=> Vector.empty), 11)
      lazy val configFilePollInterval: CaseClassParm[BootstrapConfigDto,Option[FiniteDuration]] = CaseClassParm[BootstrapConfigDto,Option[FiniteDuration]]("configFilePollInterval", _.configFilePollInterval, (d,v) => d.copy(configFilePollInterval = v), Some(()=> None), 12)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): BootstrapConfigDto = {
        BootstrapConfigDto(
          source = values(0).asInstanceOf[Option[String]],
          appName = values(1).asInstanceOf[Option[AppName]],
          consoleLogging = values(2).asInstanceOf[Option[Boolean]],
          colorConsole = values(3).asInstanceOf[Option[Boolean]],
          fileLogging = values(4).asInstanceOf[Option[Boolean]],
          logAppConfig = values(5).asInstanceOf[Option[Boolean]],
          logsDir = values(6).asInstanceOf[Option[String]],
          tempDir = values(7).asInstanceOf[Option[String]],
          cacheDir = values(8).asInstanceOf[Option[String]],
          dataDir = values(9).asInstanceOf[Option[String]],
          defaultLogLevel = values(10).asInstanceOf[Option[String]],
          logLevels = values(11).asInstanceOf[Vector[LogLevelConfig]],
          configFilePollInterval = values(12).asInstanceOf[Option[FiniteDuration]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): BootstrapConfigDto = {
        val value =
          BootstrapConfigDto(
            source = values.next().asInstanceOf[Option[String]],
            appName = values.next().asInstanceOf[Option[AppName]],
            consoleLogging = values.next().asInstanceOf[Option[Boolean]],
            colorConsole = values.next().asInstanceOf[Option[Boolean]],
            fileLogging = values.next().asInstanceOf[Option[Boolean]],
            logAppConfig = values.next().asInstanceOf[Option[Boolean]],
            logsDir = values.next().asInstanceOf[Option[String]],
            tempDir = values.next().asInstanceOf[Option[String]],
            cacheDir = values.next().asInstanceOf[Option[String]],
            dataDir = values.next().asInstanceOf[Option[String]],
            defaultLogLevel = values.next().asInstanceOf[Option[String]],
            logLevels = values.next().asInstanceOf[Vector[LogLevelConfig]],
            configFilePollInterval = values.next().asInstanceOf[Option[FiniteDuration]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(source: Option[String], appName: Option[AppName], consoleLogging: Option[Boolean], colorConsole: Option[Boolean], fileLogging: Option[Boolean], logAppConfig: Option[Boolean], logsDir: Option[String], tempDir: Option[String], cacheDir: Option[String], dataDir: Option[String], defaultLogLevel: Option[String], logLevels: Vector[LogLevelConfig], configFilePollInterval: Option[FiniteDuration]): BootstrapConfigDto =
        BootstrapConfigDto(source, appName, consoleLogging, colorConsole, fileLogging, logAppConfig, logsDir, tempDir, cacheDir, dataDir, defaultLogLevel, logLevels, configFilePollInterval)
    
    }
    
    
    lazy val typeName = "BootstrapConfigDto"
  
  }
}
