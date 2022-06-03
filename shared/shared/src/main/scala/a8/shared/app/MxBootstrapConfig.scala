package a8.shared.app

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}


/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.app.BootstrapConfig._

//====


object MxBootstrapConfig {
  
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
          .addField(_.logsDir)
          .addField(_.tempDir)
          .addField(_.cacheDir)
          .addField(_.dataDir)
          .addField(_.defaultLogLevel)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[BootstrapConfigDto] = zio.prelude.Equal.default
    
    implicit val catsEq: cats.Eq[BootstrapConfigDto] = cats.Eq.fromUniversalEquals
    
    lazy val generator: Generator[BootstrapConfigDto,parameters.type] =  {
      val constructors = Constructors[BootstrapConfigDto](10, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val source: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("source", _.source, (d,v) => d.copy(source = v), Some(()=> None), 0)
      lazy val appName: CaseClassParm[BootstrapConfigDto,Option[AppName]] = CaseClassParm[BootstrapConfigDto,Option[AppName]]("appName", _.appName, (d,v) => d.copy(appName = v), Some(()=> None), 1)
      lazy val consoleLogging: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("consoleLogging", _.consoleLogging, (d,v) => d.copy(consoleLogging = v), Some(()=> None), 2)
      lazy val colorConsole: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("colorConsole", _.colorConsole, (d,v) => d.copy(colorConsole = v), Some(()=> None), 3)
      lazy val fileLogging: CaseClassParm[BootstrapConfigDto,Option[Boolean]] = CaseClassParm[BootstrapConfigDto,Option[Boolean]]("fileLogging", _.fileLogging, (d,v) => d.copy(fileLogging = v), Some(()=> None), 4)
      lazy val logsDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("logsDir", _.logsDir, (d,v) => d.copy(logsDir = v), Some(()=> None), 5)
      lazy val tempDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("tempDir", _.tempDir, (d,v) => d.copy(tempDir = v), Some(()=> None), 6)
      lazy val cacheDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("cacheDir", _.cacheDir, (d,v) => d.copy(cacheDir = v), Some(()=> None), 7)
      lazy val dataDir: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("dataDir", _.dataDir, (d,v) => d.copy(dataDir = v), Some(()=> None), 8)
      lazy val defaultLogLevel: CaseClassParm[BootstrapConfigDto,Option[String]] = CaseClassParm[BootstrapConfigDto,Option[String]]("defaultLogLevel", _.defaultLogLevel, (d,v) => d.copy(defaultLogLevel = v), Some(()=> None), 9)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): BootstrapConfigDto = {
        BootstrapConfigDto(
          source = values(0).asInstanceOf[Option[String]],
          appName = values(1).asInstanceOf[Option[AppName]],
          consoleLogging = values(2).asInstanceOf[Option[Boolean]],
          colorConsole = values(3).asInstanceOf[Option[Boolean]],
          fileLogging = values(4).asInstanceOf[Option[Boolean]],
          logsDir = values(5).asInstanceOf[Option[String]],
          tempDir = values(6).asInstanceOf[Option[String]],
          cacheDir = values(7).asInstanceOf[Option[String]],
          dataDir = values(8).asInstanceOf[Option[String]],
          defaultLogLevel = values(9).asInstanceOf[Option[String]],
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
            logsDir = values.next().asInstanceOf[Option[String]],
            tempDir = values.next().asInstanceOf[Option[String]],
            cacheDir = values.next().asInstanceOf[Option[String]],
            dataDir = values.next().asInstanceOf[Option[String]],
            defaultLogLevel = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(source: Option[String], appName: Option[AppName], consoleLogging: Option[Boolean], colorConsole: Option[Boolean], fileLogging: Option[Boolean], logsDir: Option[String], tempDir: Option[String], cacheDir: Option[String], dataDir: Option[String], defaultLogLevel: Option[String]): BootstrapConfigDto =
        BootstrapConfigDto(source, appName, consoleLogging, colorConsole, fileLogging, logsDir, tempDir, cacheDir, dataDir, defaultLogLevel)
    
    }
    
    
    lazy val typeName = "BootstrapConfigDto"
  
  }
}
