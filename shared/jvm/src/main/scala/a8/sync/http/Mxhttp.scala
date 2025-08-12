package a8.sync.http

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.shared.SharedImports.*
import a8.sync.http.{RequestProcessorConfig, ResponseInfo, ResponseMetadata, RetryConfig}
import scala.concurrent.duration.FiniteDuration
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object Mxhttp {
  
  trait MxRetryConfig { self: RetryConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[RetryConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[RetryConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[RetryConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.maxRetries)
          .addField(_.initialBackoff)
          .addField(_.maxBackoff)
      )
      .build
    
    
    given scala.CanEqual[RetryConfig, RetryConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[RetryConfig,parameters.type] =  {
      val constructors = Constructors[RetryConfig](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val maxRetries: CaseClassParm[RetryConfig,Int] = CaseClassParm[RetryConfig,Int]("maxRetries", _.maxRetries, (d,v) => d.copy(maxRetries = v), Some(()=> 5), 0)
      lazy val initialBackoff: CaseClassParm[RetryConfig,FiniteDuration] = CaseClassParm[RetryConfig,FiniteDuration]("initialBackoff", _.initialBackoff, (d,v) => d.copy(initialBackoff = v), Some(()=> 1.second), 1)
      lazy val maxBackoff: CaseClassParm[RetryConfig,FiniteDuration] = CaseClassParm[RetryConfig,FiniteDuration]("maxBackoff", _.maxBackoff, (d,v) => d.copy(maxBackoff = v), Some(()=> 1.minute), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): RetryConfig = {
        RetryConfig(
          maxRetries = values(0).asInstanceOf[Int],
          initialBackoff = values(1).asInstanceOf[FiniteDuration],
          maxBackoff = values(2).asInstanceOf[FiniteDuration],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): RetryConfig = {
        val value =
          RetryConfig(
            maxRetries = values.next().asInstanceOf[Int],
            initialBackoff = values.next().asInstanceOf[FiniteDuration],
            maxBackoff = values.next().asInstanceOf[FiniteDuration],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(maxRetries: Int, initialBackoff: FiniteDuration, maxBackoff: FiniteDuration): RetryConfig =
        RetryConfig(maxRetries, initialBackoff, maxBackoff)
    
    }
    
    
    lazy val typeName = "RetryConfig"
  
  }
  
  
  
  
  trait MxRequestProcessorConfig { self: RequestProcessorConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[RequestProcessorConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[RequestProcessorConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[RequestProcessorConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.maxRetries)
          .addField(_.initialBackoff)
          .addField(_.maxBackoff)
          .addField(_.maxConnections)
          .addField(_.jitterFactor)
          .addField(_.backoffFactor)
          .addField(_.connectionTimeout)
          .addField(_.readTimeout)
      )
      .build
    
    
    given scala.CanEqual[RequestProcessorConfig, RequestProcessorConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[RequestProcessorConfig,parameters.type] =  {
      val constructors = Constructors[RequestProcessorConfig](8, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val maxRetries: CaseClassParm[RequestProcessorConfig,Int] = CaseClassParm[RequestProcessorConfig,Int]("maxRetries", _.maxRetries, (d,v) => d.copy(maxRetries = v), Some(()=> 5), 0)
      lazy val initialBackoff: CaseClassParm[RequestProcessorConfig,FiniteDuration] = CaseClassParm[RequestProcessorConfig,FiniteDuration]("initialBackoff", _.initialBackoff, (d,v) => d.copy(initialBackoff = v), Some(()=> 1.second), 1)
      lazy val maxBackoff: CaseClassParm[RequestProcessorConfig,FiniteDuration] = CaseClassParm[RequestProcessorConfig,FiniteDuration]("maxBackoff", _.maxBackoff, (d,v) => d.copy(maxBackoff = v), Some(()=> 1.minute), 2)
      lazy val maxConnections: CaseClassParm[RequestProcessorConfig,Int] = CaseClassParm[RequestProcessorConfig,Int]("maxConnections", _.maxConnections, (d,v) => d.copy(maxConnections = v), Some(()=> 50), 3)
      lazy val jitterFactor: CaseClassParm[RequestProcessorConfig,Double] = CaseClassParm[RequestProcessorConfig,Double]("jitterFactor", _.jitterFactor, (d,v) => d.copy(jitterFactor = v), Some(()=> 0.2d), 4)
      lazy val backoffFactor: CaseClassParm[RequestProcessorConfig,Double] = CaseClassParm[RequestProcessorConfig,Double]("backoffFactor", _.backoffFactor, (d,v) => d.copy(backoffFactor = v), Some(()=> 1.2d), 5)
      lazy val connectionTimeout: CaseClassParm[RequestProcessorConfig,FiniteDuration] = CaseClassParm[RequestProcessorConfig,FiniteDuration]("connectionTimeout", _.connectionTimeout, (d,v) => d.copy(connectionTimeout = v), Some(()=> 15.seconds), 6)
      lazy val readTimeout: CaseClassParm[RequestProcessorConfig,FiniteDuration] = CaseClassParm[RequestProcessorConfig,FiniteDuration]("readTimeout", _.readTimeout, (d,v) => d.copy(readTimeout = v), Some(()=> 30.seconds), 7)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): RequestProcessorConfig = {
        RequestProcessorConfig(
          maxRetries = values(0).asInstanceOf[Int],
          initialBackoff = values(1).asInstanceOf[FiniteDuration],
          maxBackoff = values(2).asInstanceOf[FiniteDuration],
          maxConnections = values(3).asInstanceOf[Int],
          jitterFactor = values(4).asInstanceOf[Double],
          backoffFactor = values(5).asInstanceOf[Double],
          connectionTimeout = values(6).asInstanceOf[FiniteDuration],
          readTimeout = values(7).asInstanceOf[FiniteDuration],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): RequestProcessorConfig = {
        val value =
          RequestProcessorConfig(
            maxRetries = values.next().asInstanceOf[Int],
            initialBackoff = values.next().asInstanceOf[FiniteDuration],
            maxBackoff = values.next().asInstanceOf[FiniteDuration],
            maxConnections = values.next().asInstanceOf[Int],
            jitterFactor = values.next().asInstanceOf[Double],
            backoffFactor = values.next().asInstanceOf[Double],
            connectionTimeout = values.next().asInstanceOf[FiniteDuration],
            readTimeout = values.next().asInstanceOf[FiniteDuration],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(maxRetries: Int, initialBackoff: FiniteDuration, maxBackoff: FiniteDuration, maxConnections: Int, jitterFactor: Double, backoffFactor: Double, connectionTimeout: FiniteDuration, readTimeout: FiniteDuration): RequestProcessorConfig =
        RequestProcessorConfig(maxRetries, initialBackoff, maxBackoff, maxConnections, jitterFactor, backoffFactor, connectionTimeout, readTimeout)
    
    }
    
    
    lazy val typeName = "RequestProcessorConfig"
  
  }
  
  
  
  
  trait MxResponseInfo { self: ResponseInfo.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[ResponseInfo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[ResponseInfo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[ResponseInfo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.metadata)
          .addField(_.responseBody)
      )
      .build
    
    
    given scala.CanEqual[ResponseInfo, ResponseInfo] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[ResponseInfo,parameters.type] =  {
      val constructors = Constructors[ResponseInfo](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val metadata: CaseClassParm[ResponseInfo,ResponseMetadata] = CaseClassParm[ResponseInfo,ResponseMetadata]("metadata", _.metadata, (d,v) => d.copy(metadata = v), None, 0)
      lazy val responseBody: CaseClassParm[ResponseInfo,Option[String]] = CaseClassParm[ResponseInfo,Option[String]]("responseBody", _.responseBody, (d,v) => d.copy(responseBody = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): ResponseInfo = {
        ResponseInfo(
          metadata = values(0).asInstanceOf[ResponseMetadata],
          responseBody = values(1).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): ResponseInfo = {
        val value =
          ResponseInfo(
            metadata = values.next().asInstanceOf[ResponseMetadata],
            responseBody = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(metadata: ResponseMetadata, responseBody: Option[String]): ResponseInfo =
        ResponseInfo(metadata, responseBody)
    
    }
    
    
    lazy val typeName = "ResponseInfo"
  
  }
  
  
  
  
  trait MxResponseMetadata { self: ResponseMetadata.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[ResponseMetadata,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[ResponseMetadata,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[ResponseMetadata,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.statusCodeInt)
          .addField(_.statusText)
          .addField(_.headers)
      )
      .build
    
    
    given scala.CanEqual[ResponseMetadata, ResponseMetadata] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[ResponseMetadata,parameters.type] =  {
      val constructors = Constructors[ResponseMetadata](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val statusCodeInt: CaseClassParm[ResponseMetadata,Int] = CaseClassParm[ResponseMetadata,Int]("statusCodeInt", _.statusCodeInt, (d,v) => d.copy(statusCodeInt = v), None, 0)
      lazy val statusText: CaseClassParm[ResponseMetadata,String] = CaseClassParm[ResponseMetadata,String]("statusText", _.statusText, (d,v) => d.copy(statusText = v), None, 1)
      lazy val headers: CaseClassParm[ResponseMetadata,Vector[(String,String)]] = CaseClassParm[ResponseMetadata,Vector[(String,String)]]("headers", _.headers, (d,v) => d.copy(headers = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): ResponseMetadata = {
        ResponseMetadata(
          statusCodeInt = values(0).asInstanceOf[Int],
          statusText = values(1).asInstanceOf[String],
          headers = values(2).asInstanceOf[Vector[(String,String)]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): ResponseMetadata = {
        val value =
          ResponseMetadata(
            statusCodeInt = values.next().asInstanceOf[Int],
            statusText = values.next().asInstanceOf[String],
            headers = values.next().asInstanceOf[Vector[(String,String)]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(statusCodeInt: Int, statusText: String, headers: Vector[(String,String)]): ResponseMetadata =
        ResponseMetadata(statusCodeInt, statusText, headers)
    
    }
    
    
    lazy val typeName = "ResponseMetadata"
  
  }
}
