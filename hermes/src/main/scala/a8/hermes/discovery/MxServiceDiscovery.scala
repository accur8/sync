package a8.hermes.discovery

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import java.time.Instant
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxServiceDiscovery {
  
  trait MxDiscoveryRequest { self: DiscoveryRequest.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[DiscoveryRequest,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[DiscoveryRequest,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[DiscoveryRequest,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.request_id)
          .addField(_.reply_suffix)
          .addField(_.timestamp)
          .addField(_.query)
      )
      .build
    
    
    given scala.CanEqual[DiscoveryRequest, DiscoveryRequest] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[DiscoveryRequest,parameters.type] =  {
      val constructors = Constructors[DiscoveryRequest](4, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val request_id: CaseClassParm[DiscoveryRequest,String] = CaseClassParm[DiscoveryRequest,String]("request_id", _.request_id, (d,v) => d.copy(request_id = v), None, 0)
      lazy val reply_suffix: CaseClassParm[DiscoveryRequest,String] = CaseClassParm[DiscoveryRequest,String]("reply_suffix", _.reply_suffix, (d,v) => d.copy(reply_suffix = v), None, 1)
      lazy val timestamp: CaseClassParm[DiscoveryRequest,Instant] = CaseClassParm[DiscoveryRequest,Instant]("timestamp", _.timestamp, (d,v) => d.copy(timestamp = v), None, 2)
      lazy val query: CaseClassParm[DiscoveryRequest,DiscoveryQuery] = CaseClassParm[DiscoveryRequest,DiscoveryQuery]("query", _.query, (d,v) => d.copy(query = v), None, 3)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): DiscoveryRequest = {
        DiscoveryRequest(
          request_id = values(0).asInstanceOf[String],
          reply_suffix = values(1).asInstanceOf[String],
          timestamp = values(2).asInstanceOf[Instant],
          query = values(3).asInstanceOf[DiscoveryQuery],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): DiscoveryRequest = {
        val value =
          DiscoveryRequest(
            request_id = values.next().asInstanceOf[String],
            reply_suffix = values.next().asInstanceOf[String],
            timestamp = values.next().asInstanceOf[Instant],
            query = values.next().asInstanceOf[DiscoveryQuery],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(request_id: String, reply_suffix: String, timestamp: Instant, query: DiscoveryQuery): DiscoveryRequest =
        DiscoveryRequest(request_id, reply_suffix, timestamp, query)
    
    }
    
    
    lazy val typeName = "DiscoveryRequest"
  
  }
  
  
  
  
  trait MxDiscoveryResponse { self: DiscoveryResponse.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[DiscoveryResponse,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[DiscoveryResponse,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[DiscoveryResponse,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.request_id)
          .addField(_.processUid)
          .addField(_.unixPid)
          .addField(_.app_name)
          .addField(_.mailbox_address)
          .addField(_.service_name)
          .addField(_.codebase_name)
          .addField(_.timestamp)
          .addField(_.capabilities)
          .addField(_.metadata)
          .addField(_.extended_metadata)
          .addField(_.service_discovery_mapping)
      )
      .build
    
    
    given scala.CanEqual[DiscoveryResponse, DiscoveryResponse] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[DiscoveryResponse,parameters.type] =  {
      val constructors = Constructors[DiscoveryResponse](12, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val request_id: CaseClassParm[DiscoveryResponse,String] = CaseClassParm[DiscoveryResponse,String]("request_id", _.request_id, (d,v) => d.copy(request_id = v), None, 0)
      lazy val processUid: CaseClassParm[DiscoveryResponse,String] = CaseClassParm[DiscoveryResponse,String]("processUid", _.processUid, (d,v) => d.copy(processUid = v), Some(()=> ""), 1)
      lazy val unixPid: CaseClassParm[DiscoveryResponse,Int] = CaseClassParm[DiscoveryResponse,Int]("unixPid", _.unixPid, (d,v) => d.copy(unixPid = v), None, 2)
      lazy val app_name: CaseClassParm[DiscoveryResponse,String] = CaseClassParm[DiscoveryResponse,String]("app_name", _.app_name, (d,v) => d.copy(app_name = v), None, 3)
      lazy val mailbox_address: CaseClassParm[DiscoveryResponse,String] = CaseClassParm[DiscoveryResponse,String]("mailbox_address", _.mailbox_address, (d,v) => d.copy(mailbox_address = v), None, 4)
      lazy val service_name: CaseClassParm[DiscoveryResponse,String] = CaseClassParm[DiscoveryResponse,String]("service_name", _.service_name, (d,v) => d.copy(service_name = v), Some(()=> ""), 5)
      lazy val codebase_name: CaseClassParm[DiscoveryResponse,String] = CaseClassParm[DiscoveryResponse,String]("codebase_name", _.codebase_name, (d,v) => d.copy(codebase_name = v), Some(()=> "sync-scala"), 6)
      lazy val timestamp: CaseClassParm[DiscoveryResponse,Instant] = CaseClassParm[DiscoveryResponse,Instant]("timestamp", _.timestamp, (d,v) => d.copy(timestamp = v), None, 7)
      lazy val capabilities: CaseClassParm[DiscoveryResponse,ProcessCapabilities] = CaseClassParm[DiscoveryResponse,ProcessCapabilities]("capabilities", _.capabilities, (d,v) => d.copy(capabilities = v), None, 8)
      lazy val metadata: CaseClassParm[DiscoveryResponse,Map[String,String]] = CaseClassParm[DiscoveryResponse,Map[String,String]]("metadata", _.metadata, (d,v) => d.copy(metadata = v), None, 9)
      lazy val extended_metadata: CaseClassParm[DiscoveryResponse,Option[Map[String,String]]] = CaseClassParm[DiscoveryResponse,Option[Map[String,String]]]("extended_metadata", _.extended_metadata, (d,v) => d.copy(extended_metadata = v), None, 10)
      lazy val service_discovery_mapping: CaseClassParm[DiscoveryResponse,Map[String,String]] = CaseClassParm[DiscoveryResponse,Map[String,String]]("service_discovery_mapping", _.service_discovery_mapping, (d,v) => d.copy(service_discovery_mapping = v), None, 11)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): DiscoveryResponse = {
        DiscoveryResponse(
          request_id = values(0).asInstanceOf[String],
          processUid = values(1).asInstanceOf[String],
          unixPid = values(2).asInstanceOf[Int],
          app_name = values(3).asInstanceOf[String],
          mailbox_address = values(4).asInstanceOf[String],
          service_name = values(5).asInstanceOf[String],
          codebase_name = values(6).asInstanceOf[String],
          timestamp = values(7).asInstanceOf[Instant],
          capabilities = values(8).asInstanceOf[ProcessCapabilities],
          metadata = values(9).asInstanceOf[Map[String,String]],
          extended_metadata = values(10).asInstanceOf[Option[Map[String,String]]],
          service_discovery_mapping = values(11).asInstanceOf[Map[String,String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): DiscoveryResponse = {
        val value =
          DiscoveryResponse(
            request_id = values.next().asInstanceOf[String],
            processUid = values.next().asInstanceOf[String],
            unixPid = values.next().asInstanceOf[Int],
            app_name = values.next().asInstanceOf[String],
            mailbox_address = values.next().asInstanceOf[String],
            service_name = values.next().asInstanceOf[String],
            codebase_name = values.next().asInstanceOf[String],
            timestamp = values.next().asInstanceOf[Instant],
            capabilities = values.next().asInstanceOf[ProcessCapabilities],
            metadata = values.next().asInstanceOf[Map[String,String]],
            extended_metadata = values.next().asInstanceOf[Option[Map[String,String]]],
            service_discovery_mapping = values.next().asInstanceOf[Map[String,String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(request_id: String, processUid: String, unixPid: Int, app_name: String, mailbox_address: String, service_name: String, codebase_name: String, timestamp: Instant, capabilities: ProcessCapabilities, metadata: Map[String,String], extended_metadata: Option[Map[String,String]], service_discovery_mapping: Map[String,String]): DiscoveryResponse =
        DiscoveryResponse(request_id, processUid, unixPid, app_name, mailbox_address, service_name, codebase_name, timestamp, capabilities, metadata, extended_metadata, service_discovery_mapping)
    
    }
    
    
    lazy val typeName = "DiscoveryResponse"
  
  }
  
  
  
  
  trait MxDiscoveryQuery { self: DiscoveryQuery.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[DiscoveryQuery,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[DiscoveryQuery,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[DiscoveryQuery,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.implements_rpc)
          .addField(_.app_name)
          .addField(_.service_name)
          .addField(_.location)
          .addField(_.include_extended_metadata)
          .addField(_.include_schema_details)
      )
      .build
    
    
    given scala.CanEqual[DiscoveryQuery, DiscoveryQuery] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[DiscoveryQuery,parameters.type] =  {
      val constructors = Constructors[DiscoveryQuery](6, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val implements_rpc: CaseClassParm[DiscoveryQuery,Iterable[String]] = CaseClassParm[DiscoveryQuery,Iterable[String]]("implements_rpc", _.implements_rpc, (d,v) => d.copy(implements_rpc = v), Some(()=> Iterable.empty), 0)
      lazy val app_name: CaseClassParm[DiscoveryQuery,Option[String]] = CaseClassParm[DiscoveryQuery,Option[String]]("app_name", _.app_name, (d,v) => d.copy(app_name = v), Some(()=> None), 1)
      lazy val service_name: CaseClassParm[DiscoveryQuery,Option[String]] = CaseClassParm[DiscoveryQuery,Option[String]]("service_name", _.service_name, (d,v) => d.copy(service_name = v), Some(()=> None), 2)
      lazy val location: CaseClassParm[DiscoveryQuery,Option[LocationQuery]] = CaseClassParm[DiscoveryQuery,Option[LocationQuery]]("location", _.location, (d,v) => d.copy(location = v), Some(()=> None), 3)
      lazy val include_extended_metadata: CaseClassParm[DiscoveryQuery,Boolean] = CaseClassParm[DiscoveryQuery,Boolean]("include_extended_metadata", _.include_extended_metadata, (d,v) => d.copy(include_extended_metadata = v), Some(()=> false), 4)
      lazy val include_schema_details: CaseClassParm[DiscoveryQuery,Boolean] = CaseClassParm[DiscoveryQuery,Boolean]("include_schema_details", _.include_schema_details, (d,v) => d.copy(include_schema_details = v), Some(()=> false), 5)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): DiscoveryQuery = {
        DiscoveryQuery(
          implements_rpc = values(0).asInstanceOf[Iterable[String]],
          app_name = values(1).asInstanceOf[Option[String]],
          service_name = values(2).asInstanceOf[Option[String]],
          location = values(3).asInstanceOf[Option[LocationQuery]],
          include_extended_metadata = values(4).asInstanceOf[Boolean],
          include_schema_details = values(5).asInstanceOf[Boolean],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): DiscoveryQuery = {
        val value =
          DiscoveryQuery(
            implements_rpc = values.next().asInstanceOf[Iterable[String]],
            app_name = values.next().asInstanceOf[Option[String]],
            service_name = values.next().asInstanceOf[Option[String]],
            location = values.next().asInstanceOf[Option[LocationQuery]],
            include_extended_metadata = values.next().asInstanceOf[Boolean],
            include_schema_details = values.next().asInstanceOf[Boolean],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(implements_rpc: Iterable[String], app_name: Option[String], service_name: Option[String], location: Option[LocationQuery], include_extended_metadata: Boolean, include_schema_details: Boolean): DiscoveryQuery =
        DiscoveryQuery(implements_rpc, app_name, service_name, location, include_extended_metadata, include_schema_details)
    
    }
    
    
    lazy val typeName = "DiscoveryQuery"
  
  }
  
  
  
  
  trait MxLocationQuery { self: LocationQuery.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[LocationQuery,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[LocationQuery,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[LocationQuery,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.server)
          .addField(_.user)
          .addField(_.user_server)
      )
      .build
    
    
    given scala.CanEqual[LocationQuery, LocationQuery] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[LocationQuery,parameters.type] =  {
      val constructors = Constructors[LocationQuery](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val server: CaseClassParm[LocationQuery,Option[String]] = CaseClassParm[LocationQuery,Option[String]]("server", _.server, (d,v) => d.copy(server = v), Some(()=> None), 0)
      lazy val user: CaseClassParm[LocationQuery,Option[String]] = CaseClassParm[LocationQuery,Option[String]]("user", _.user, (d,v) => d.copy(user = v), Some(()=> None), 1)
      lazy val user_server: CaseClassParm[LocationQuery,Option[String]] = CaseClassParm[LocationQuery,Option[String]]("user_server", _.user_server, (d,v) => d.copy(user_server = v), Some(()=> None), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): LocationQuery = {
        LocationQuery(
          server = values(0).asInstanceOf[Option[String]],
          user = values(1).asInstanceOf[Option[String]],
          user_server = values(2).asInstanceOf[Option[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): LocationQuery = {
        val value =
          LocationQuery(
            server = values.next().asInstanceOf[Option[String]],
            user = values.next().asInstanceOf[Option[String]],
            user_server = values.next().asInstanceOf[Option[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(server: Option[String], user: Option[String], user_server: Option[String]): LocationQuery =
        LocationQuery(server, user, user_server)
    
    }
    
    
    lazy val typeName = "LocationQuery"
  
  }
  
  
  
  
  trait MxRpcMethod { self: RpcMethod.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[RpcMethod,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[RpcMethod,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[RpcMethod,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.formats)
          .addField(_.request_type)
          .addField(_.response_type)
          .addField(_.description)
      )
      .build
    
    
    given scala.CanEqual[RpcMethod, RpcMethod] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[RpcMethod,parameters.type] =  {
      val constructors = Constructors[RpcMethod](5, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[RpcMethod,String] = CaseClassParm[RpcMethod,String]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val formats: CaseClassParm[RpcMethod,Iterable[SerializationFormat]] = CaseClassParm[RpcMethod,Iterable[SerializationFormat]]("formats", _.formats, (d,v) => d.copy(formats = v), Some(()=> Iterable.empty), 1)
      lazy val request_type: CaseClassParm[RpcMethod,String] = CaseClassParm[RpcMethod,String]("request_type", _.request_type, (d,v) => d.copy(request_type = v), Some(()=> ""), 2)
      lazy val response_type: CaseClassParm[RpcMethod,String] = CaseClassParm[RpcMethod,String]("response_type", _.response_type, (d,v) => d.copy(response_type = v), Some(()=> ""), 3)
      lazy val description: CaseClassParm[RpcMethod,String] = CaseClassParm[RpcMethod,String]("description", _.description, (d,v) => d.copy(description = v), Some(()=> ""), 4)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): RpcMethod = {
        RpcMethod(
          name = values(0).asInstanceOf[String],
          formats = values(1).asInstanceOf[Iterable[SerializationFormat]],
          request_type = values(2).asInstanceOf[String],
          response_type = values(3).asInstanceOf[String],
          description = values(4).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): RpcMethod = {
        val value =
          RpcMethod(
            name = values.next().asInstanceOf[String],
            formats = values.next().asInstanceOf[Iterable[SerializationFormat]],
            request_type = values.next().asInstanceOf[String],
            response_type = values.next().asInstanceOf[String],
            description = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: String, formats: Iterable[SerializationFormat], request_type: String, response_type: String, description: String): RpcMethod =
        RpcMethod(name, formats, request_type, response_type, description)
    
    }
    
    
    lazy val typeName = "RpcMethod"
  
  }
  
  
  
  
  trait MxRpcSchemaInfo { self: RpcSchemaInfo.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[RpcSchemaInfo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[RpcSchemaInfo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[RpcSchemaInfo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.methods)
          .addField(_.default_formats)
          .addField(_.schema_version)
          .addField(_.description)
      )
      .build
    
    
    given scala.CanEqual[RpcSchemaInfo, RpcSchemaInfo] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[RpcSchemaInfo,parameters.type] =  {
      val constructors = Constructors[RpcSchemaInfo](5, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[RpcSchemaInfo,String] = CaseClassParm[RpcSchemaInfo,String]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val methods: CaseClassParm[RpcSchemaInfo,Iterable[RpcMethod]] = CaseClassParm[RpcSchemaInfo,Iterable[RpcMethod]]("methods", _.methods, (d,v) => d.copy(methods = v), Some(()=> Iterable.empty), 1)
      lazy val default_formats: CaseClassParm[RpcSchemaInfo,Iterable[SerializationFormat]] = CaseClassParm[RpcSchemaInfo,Iterable[SerializationFormat]]("default_formats", _.default_formats, (d,v) => d.copy(default_formats = v), Some(()=> Iterable.empty), 2)
      lazy val schema_version: CaseClassParm[RpcSchemaInfo,Int] = CaseClassParm[RpcSchemaInfo,Int]("schema_version", _.schema_version, (d,v) => d.copy(schema_version = v), Some(()=> 0), 3)
      lazy val description: CaseClassParm[RpcSchemaInfo,String] = CaseClassParm[RpcSchemaInfo,String]("description", _.description, (d,v) => d.copy(description = v), Some(()=> ""), 4)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): RpcSchemaInfo = {
        RpcSchemaInfo(
          name = values(0).asInstanceOf[String],
          methods = values(1).asInstanceOf[Iterable[RpcMethod]],
          default_formats = values(2).asInstanceOf[Iterable[SerializationFormat]],
          schema_version = values(3).asInstanceOf[Int],
          description = values(4).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): RpcSchemaInfo = {
        val value =
          RpcSchemaInfo(
            name = values.next().asInstanceOf[String],
            methods = values.next().asInstanceOf[Iterable[RpcMethod]],
            default_formats = values.next().asInstanceOf[Iterable[SerializationFormat]],
            schema_version = values.next().asInstanceOf[Int],
            description = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: String, methods: Iterable[RpcMethod], default_formats: Iterable[SerializationFormat], schema_version: Int, description: String): RpcSchemaInfo =
        RpcSchemaInfo(name, methods, default_formats, schema_version, description)
    
    }
    
    
    lazy val typeName = "RpcSchemaInfo"
  
  }
  
  
  
  
  trait MxProcessCapabilities { self: ProcessCapabilities.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[ProcessCapabilities,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[ProcessCapabilities,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[ProcessCapabilities,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.implements_rpc)
          .addField(_.rpc_schemas)
          .addField(_.rpc_schema_details)
          .addField(_.location)
          .addField(_.engines)
      )
      .build
    
    
    given scala.CanEqual[ProcessCapabilities, ProcessCapabilities] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[ProcessCapabilities,parameters.type] =  {
      val constructors = Constructors[ProcessCapabilities](5, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val implements_rpc: CaseClassParm[ProcessCapabilities,Iterable[String]] = CaseClassParm[ProcessCapabilities,Iterable[String]]("implements_rpc", _.implements_rpc, (d,v) => d.copy(implements_rpc = v), None, 0)
      lazy val rpc_schemas: CaseClassParm[ProcessCapabilities,Map[String,Iterable[String]]] = CaseClassParm[ProcessCapabilities,Map[String,Iterable[String]]]("rpc_schemas", _.rpc_schemas, (d,v) => d.copy(rpc_schemas = v), None, 1)
      lazy val rpc_schema_details: CaseClassParm[ProcessCapabilities,Iterable[RpcSchemaInfo]] = CaseClassParm[ProcessCapabilities,Iterable[RpcSchemaInfo]]("rpc_schema_details", _.rpc_schema_details, (d,v) => d.copy(rpc_schema_details = v), Some(()=> Iterable.empty), 2)
      lazy val location: CaseClassParm[ProcessCapabilities,ProcessLocation] = CaseClassParm[ProcessCapabilities,ProcessLocation]("location", _.location, (d,v) => d.copy(location = v), None, 3)
      lazy val engines: CaseClassParm[ProcessCapabilities,Iterable[String]] = CaseClassParm[ProcessCapabilities,Iterable[String]]("engines", _.engines, (d,v) => d.copy(engines = v), Some(()=> Iterable.empty), 4)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): ProcessCapabilities = {
        ProcessCapabilities(
          implements_rpc = values(0).asInstanceOf[Iterable[String]],
          rpc_schemas = values(1).asInstanceOf[Map[String,Iterable[String]]],
          rpc_schema_details = values(2).asInstanceOf[Iterable[RpcSchemaInfo]],
          location = values(3).asInstanceOf[ProcessLocation],
          engines = values(4).asInstanceOf[Iterable[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): ProcessCapabilities = {
        val value =
          ProcessCapabilities(
            implements_rpc = values.next().asInstanceOf[Iterable[String]],
            rpc_schemas = values.next().asInstanceOf[Map[String,Iterable[String]]],
            rpc_schema_details = values.next().asInstanceOf[Iterable[RpcSchemaInfo]],
            location = values.next().asInstanceOf[ProcessLocation],
            engines = values.next().asInstanceOf[Iterable[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(implements_rpc: Iterable[String], rpc_schemas: Map[String,Iterable[String]], rpc_schema_details: Iterable[RpcSchemaInfo], location: ProcessLocation, engines: Iterable[String]): ProcessCapabilities =
        ProcessCapabilities(implements_rpc, rpc_schemas, rpc_schema_details, location, engines)
    
    }
    
    
    lazy val typeName = "ProcessCapabilities"
  
  }
  
  
  
  
  trait MxProcessLocation { self: ProcessLocation.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[ProcessLocation,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[ProcessLocation,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[ProcessLocation,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.server)
          .addField(_.user)
          .addField(_.ip_addresses)
      )
      .build
    
    
    given scala.CanEqual[ProcessLocation, ProcessLocation] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[ProcessLocation,parameters.type] =  {
      val constructors = Constructors[ProcessLocation](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val server: CaseClassParm[ProcessLocation,String] = CaseClassParm[ProcessLocation,String]("server", _.server, (d,v) => d.copy(server = v), None, 0)
      lazy val user: CaseClassParm[ProcessLocation,String] = CaseClassParm[ProcessLocation,String]("user", _.user, (d,v) => d.copy(user = v), None, 1)
      lazy val ip_addresses: CaseClassParm[ProcessLocation,Iterable[String]] = CaseClassParm[ProcessLocation,Iterable[String]]("ip_addresses", _.ip_addresses, (d,v) => d.copy(ip_addresses = v), Some(()=> Iterable.empty), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): ProcessLocation = {
        ProcessLocation(
          server = values(0).asInstanceOf[String],
          user = values(1).asInstanceOf[String],
          ip_addresses = values(2).asInstanceOf[Iterable[String]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): ProcessLocation = {
        val value =
          ProcessLocation(
            server = values.next().asInstanceOf[String],
            user = values.next().asInstanceOf[String],
            ip_addresses = values.next().asInstanceOf[Iterable[String]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(server: String, user: String, ip_addresses: Iterable[String]): ProcessLocation =
        ProcessLocation(server, user, ip_addresses)
    
    }
    
    
    lazy val typeName = "ProcessLocation"
  
  }
}
