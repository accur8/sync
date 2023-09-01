package a8.sync.qubes

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.sync.qubes.QubesApiClient.Config
import sttp.model.Uri
import a8.sync.http.{RequestProcessorConfig, RetryConfig}
import scala.concurrent.duration.FiniteDuration
import a8.sync.qubes.QubesApiClient._
import UpdateRowRequest.Parameter
import a8.shared.json.ast.{JsDoc, JsObj}
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxQubesApiClient {
  
  trait MxConfig {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Config,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Config,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Config,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.uri)
          .addField(_.authToken)
          .addField(_.requestProcessor)
      )
      .build
    
    
    given scala.CanEqual[Config, Config] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[Config,parameters.type] =  {
      val constructors = Constructors[Config](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val uri: CaseClassParm[Config,Uri] = CaseClassParm[Config,Uri]("uri", _.uri, (d,v) => d.copy(uri = v), None, 0)
      lazy val authToken: CaseClassParm[Config,AuthToken] = CaseClassParm[Config,AuthToken]("authToken", _.authToken, (d,v) => d.copy(authToken = v), None, 1)
      lazy val requestProcessor: CaseClassParm[Config,RequestProcessorConfig] = CaseClassParm[Config,RequestProcessorConfig]("requestProcessor", _.requestProcessor, (d,v) => d.copy(requestProcessor = v), Some(()=> RequestProcessorConfig.default), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Config = {
        Config(
          uri = values(0).asInstanceOf[Uri],
          authToken = values(1).asInstanceOf[AuthToken],
          requestProcessor = values(2).asInstanceOf[RequestProcessorConfig],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Config = {
        val value =
          Config(
            uri = values.next().asInstanceOf[Uri],
            authToken = values.next().asInstanceOf[AuthToken],
            requestProcessor = values.next().asInstanceOf[RequestProcessorConfig],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(uri: Uri, authToken: AuthToken, requestProcessor: RequestProcessorConfig): Config =
        Config(uri, authToken, requestProcessor)
    
    }
    
    
    lazy val typeName = "Config"
  
  }
  
  
  
  
  trait MxQueryRequest {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[QueryRequest,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[QueryRequest,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[QueryRequest,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.query)
          .addField(_.dataFormat)
          .addField(_.appSpace)
      )
      .build
    
    
    given scala.CanEqual[QueryRequest, QueryRequest] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[QueryRequest,parameters.type] =  {
      val constructors = Constructors[QueryRequest](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val query: CaseClassParm[QueryRequest,String] = CaseClassParm[QueryRequest,String]("query", _.query, (d,v) => d.copy(query = v), None, 0)
      lazy val dataFormat: CaseClassParm[QueryRequest,String] = CaseClassParm[QueryRequest,String]("dataFormat", _.dataFormat, (d,v) => d.copy(dataFormat = v), Some(()=> QueryRequest.verbose), 1)
      lazy val appSpace: CaseClassParm[QueryRequest,Option[String]] = CaseClassParm[QueryRequest,Option[String]]("appSpace", _.appSpace, (d,v) => d.copy(appSpace = v), Some(()=> None), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): QueryRequest = {
        QueryRequest(
          query = values(0).asInstanceOf[String],
          dataFormat = values(1).asInstanceOf[String],
          appSpace = values(2).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): QueryRequest = {
        val value =
          QueryRequest(
            query = values.next().asInstanceOf[String],
            dataFormat = values.next().asInstanceOf[String],
            appSpace = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(query: String, dataFormat: String, appSpace: Option[String]): QueryRequest =
        QueryRequest(query, dataFormat, appSpace)
    
    }
    
    
    lazy val typeName = "QueryRequest"
  
  }
  
  
  
  
  trait MxParameter {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[Parameter,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[Parameter,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[Parameter,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.dataType)
          .addField(_.cube)
          .addField(_.field)
          .addField(_.value)
      )
      .build
    
    
    given scala.CanEqual[Parameter, Parameter] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[Parameter,parameters.type] =  {
      val constructors = Constructors[Parameter](4, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val dataType: CaseClassParm[Parameter,Option[String]] = CaseClassParm[Parameter,Option[String]]("dataType", _.dataType, (d,v) => d.copy(dataType = v), None, 0)
      lazy val cube: CaseClassParm[Parameter,Option[String]] = CaseClassParm[Parameter,Option[String]]("cube", _.cube, (d,v) => d.copy(cube = v), None, 1)
      lazy val field: CaseClassParm[Parameter,Option[String]] = CaseClassParm[Parameter,Option[String]]("field", _.field, (d,v) => d.copy(field = v), None, 2)
      lazy val value: CaseClassParm[Parameter,String] = CaseClassParm[Parameter,String]("value", _.value, (d,v) => d.copy(value = v), None, 3)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): Parameter = {
        Parameter(
          dataType = values(0).asInstanceOf[Option[String]],
          cube = values(1).asInstanceOf[Option[String]],
          field = values(2).asInstanceOf[Option[String]],
          value = values(3).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): Parameter = {
        val value =
          Parameter(
            dataType = values.next().asInstanceOf[Option[String]],
            cube = values.next().asInstanceOf[Option[String]],
            field = values.next().asInstanceOf[Option[String]],
            value = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(dataType: Option[String], cube: Option[String], field: Option[String], value: String): Parameter =
        Parameter(dataType, cube, field, value)
    
    }
    
    
    lazy val typeName = "Parameter"
  
  }
  
  
  
  
  trait MxUpdateRowRequest {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[UpdateRowRequest,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[UpdateRowRequest,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[UpdateRowRequest,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.cube)
          .addField(_.fields)
          .addField(_.parameters)
          .addField(_.where)
          .addField(_.appSpace)
      )
      .build
    
    
    given scala.CanEqual[UpdateRowRequest, UpdateRowRequest] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[UpdateRowRequest,parameters.type] =  {
      val constructors = Constructors[UpdateRowRequest](5, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val cube: CaseClassParm[UpdateRowRequest,String] = CaseClassParm[UpdateRowRequest,String]("cube", _.cube, (d,v) => d.copy(cube = v), None, 0)
      lazy val fields: CaseClassParm[UpdateRowRequest,JsObj] = CaseClassParm[UpdateRowRequest,JsObj]("fields", _.fields, (d,v) => d.copy(fields = v), None, 1)
      lazy val parameters: CaseClassParm[UpdateRowRequest,Vector[Parameter]] = CaseClassParm[UpdateRowRequest,Vector[Parameter]]("parameters", _.parameters, (d,v) => d.copy(parameters = v), Some(()=> Vector.empty[Parameter]), 2)
      lazy val where: CaseClassParm[UpdateRowRequest,Option[String]] = CaseClassParm[UpdateRowRequest,Option[String]]("where", _.where, (d,v) => d.copy(where = v), Some(()=> None), 3)
      lazy val appSpace: CaseClassParm[UpdateRowRequest,Option[String]] = CaseClassParm[UpdateRowRequest,Option[String]]("appSpace", _.appSpace, (d,v) => d.copy(appSpace = v), Some(()=> None), 4)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): UpdateRowRequest = {
        UpdateRowRequest(
          cube = values(0).asInstanceOf[String],
          fields = values(1).asInstanceOf[JsObj],
          parameters = values(2).asInstanceOf[Vector[Parameter]],
          where = values(3).asInstanceOf[Option[String]],
          appSpace = values(4).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): UpdateRowRequest = {
        val value =
          UpdateRowRequest(
            cube = values.next().asInstanceOf[String],
            fields = values.next().asInstanceOf[JsObj],
            parameters = values.next().asInstanceOf[Vector[Parameter]],
            where = values.next().asInstanceOf[Option[String]],
            appSpace = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(cube: String, fields: JsObj, parameters: Vector[Parameter], where: Option[String], appSpace: Option[String]): UpdateRowRequest =
        UpdateRowRequest(cube, fields, parameters, where, appSpace)
    
    }
    
    
    lazy val typeName = "UpdateRowRequest"
  
  }
  
  
  
  
  trait MxUpdateRowResponse {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[UpdateRowResponse,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[UpdateRowResponse,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[UpdateRowResponse,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.success)
          .addField(_.validationFailures)
          .addField(_.errorMessage)
          .addField(_.serverStackTrace)
          .addField(_.numberOfRowsUpdated)
          .addField(_.keys)
      )
      .build
    
    
    given scala.CanEqual[UpdateRowResponse, UpdateRowResponse] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[UpdateRowResponse,parameters.type] =  {
      val constructors = Constructors[UpdateRowResponse](6, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val success: CaseClassParm[UpdateRowResponse,Boolean] = CaseClassParm[UpdateRowResponse,Boolean]("success", _.success, (d,v) => d.copy(success = v), None, 0)
      lazy val validationFailures: CaseClassParm[UpdateRowResponse,Option[JsObj]] = CaseClassParm[UpdateRowResponse,Option[JsObj]]("validationFailures", _.validationFailures, (d,v) => d.copy(validationFailures = v), Some(()=> None), 1)
      lazy val errorMessage: CaseClassParm[UpdateRowResponse,Option[String]] = CaseClassParm[UpdateRowResponse,Option[String]]("errorMessage", _.errorMessage, (d,v) => d.copy(errorMessage = v), Some(()=> None), 2)
      lazy val serverStackTrace: CaseClassParm[UpdateRowResponse,Option[String]] = CaseClassParm[UpdateRowResponse,Option[String]]("serverStackTrace", _.serverStackTrace, (d,v) => d.copy(serverStackTrace = v), Some(()=> None), 3)
      lazy val numberOfRowsUpdated: CaseClassParm[UpdateRowResponse,Int] = CaseClassParm[UpdateRowResponse,Int]("numberOfRowsUpdated", _.numberOfRowsUpdated, (d,v) => d.copy(numberOfRowsUpdated = v), Some(()=> 0), 4)
      lazy val keys: CaseClassParm[UpdateRowResponse,JsObj] = CaseClassParm[UpdateRowResponse,JsObj]("keys", _.keys, (d,v) => d.copy(keys = v), Some(()=> JsObj.empty), 5)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): UpdateRowResponse = {
        UpdateRowResponse(
          success = values(0).asInstanceOf[Boolean],
          validationFailures = values(1).asInstanceOf[Option[JsObj]],
          errorMessage = values(2).asInstanceOf[Option[String]],
          serverStackTrace = values(3).asInstanceOf[Option[String]],
          numberOfRowsUpdated = values(4).asInstanceOf[Int],
          keys = values(5).asInstanceOf[JsObj],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): UpdateRowResponse = {
        val value =
          UpdateRowResponse(
            success = values.next().asInstanceOf[Boolean],
            validationFailures = values.next().asInstanceOf[Option[JsObj]],
            errorMessage = values.next().asInstanceOf[Option[String]],
            serverStackTrace = values.next().asInstanceOf[Option[String]],
            numberOfRowsUpdated = values.next().asInstanceOf[Int],
            keys = values.next().asInstanceOf[JsObj],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(success: Boolean, validationFailures: Option[JsObj], errorMessage: Option[String], serverStackTrace: Option[String], numberOfRowsUpdated: Int, keys: JsObj): UpdateRowResponse =
        UpdateRowResponse(success, validationFailures, errorMessage, serverStackTrace, numberOfRowsUpdated, keys)
    
    }
    
    
    lazy val typeName = "UpdateRowResponse"
  
  }
}
