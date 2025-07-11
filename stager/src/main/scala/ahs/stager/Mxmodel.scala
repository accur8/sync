package ahs.stager

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
// noop import so IDE generated imports get put inside the comments block, this can be removed once you have at least one other import
import _root_.scala
import a8.shared.jdbcf.DatabaseConfig.Password
import a8.shared.jdbcf.{DatabaseConfig, TableName}
import ahs.stager.model.*
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}
import a8.shared.jdbcf


object Mxmodel {
  
  trait MxTableInfo { self: TableInfo.type =>
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.KeyedTableMapper[TableInfo,TableName] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.okfil)
        .addField(_.okdiv)    
        .tableName("O1PMBR")
        .singlePrimaryKey(_.okfil)
        .buildKeyedTableMapper
    
    
    class TableDsl(join: jdbcf.querydsl.QueryDsl.Join = jdbcf.querydsl.QueryDsl.RootJoin) {
      val okfil = jdbcf.querydsl.QueryDsl.field[TableName]("okfil", join)
      val okdiv = jdbcf.querydsl.QueryDsl.field[DivX]("okdiv", join)
    
    }
    
    val queryDsl = new jdbcf.querydsl.QueryDsl[TableInfo, TableDsl, TableName](jdbcMapper, new TableDsl)
    
    def query(whereFn: TableDsl => jdbcf.querydsl.QueryDsl.Condition): jdbcf.querydsl.SelectQuery[TableInfo, TableDsl] =
      queryDsl.query(whereFn)
    
    def update(set: TableDsl => Iterable[jdbcf.querydsl.UpdateQuery.Assignment[?]]): jdbcf.querydsl.UpdateQuery[TableDsl] =
      queryDsl.update(set)
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[TableInfo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[TableInfo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[TableInfo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.okfil)
          .addField(_.okdiv)
      )
      .build
    
    
    given scala.CanEqual[TableInfo, TableInfo] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[TableInfo,parameters.type] =  {
      val constructors = Constructors[TableInfo](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val okfil: CaseClassParm[TableInfo,TableName] = CaseClassParm[TableInfo,TableName]("okfil", _.okfil, (d,v) => d.copy(okfil = v), None, 0)
      lazy val okdiv: CaseClassParm[TableInfo,DivX] = CaseClassParm[TableInfo,DivX]("okdiv", _.okdiv, (d,v) => d.copy(okdiv = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): TableInfo = {
        TableInfo(
          okfil = values(0).asInstanceOf[TableName],
          okdiv = values(1).asInstanceOf[DivX],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): TableInfo = {
        val value =
          TableInfo(
            okfil = values.next().asInstanceOf[TableName],
            okdiv = values.next().asInstanceOf[DivX],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(okfil: TableName, okdiv: DivX): TableInfo =
        TableInfo(okfil, okdiv)
    
    }
    
    
    lazy val typeName = "TableInfo"
  
  }
  
  
  
  
  trait MxClientInfo { self: ClientInfo.type =>
  
    implicit lazy val jdbcMapper: a8.shared.jdbcf.mapper.KeyedTableMapper[ClientInfo,ClientId] =
      a8.shared.jdbcf.mapper.MapperBuilder(generator)
        .addField(_.cbdiv)
        .addField(_.cbdiva)
        .addField(_.cbdivb)
        .addField(_.cbdivc)
        .addField(_.cbdivd)
        .addField(_.cbdive)
        .addField(_.cbdivf)
        .addField(_.cbdivg)
        .addField(_.cbdivh)
        .addField(_.cbdivi)
        .addField(_.cbdivj)
        .addField(_.cbdivk)
        .addField(_.cbdivl)
        .addField(_.cbdivm)
        .addField(_.cbdivn)
        .addField(_.cbdivo)
        .addField(_.cbdivp)
        .addField(_.cbdivq)
        .addField(_.cbdivr)
        .addField(_.cbdivs)
        .addField(_.cbdivt)
        .addField(_.cbdivu)
        .addField(_.cbdivv)
        .addField(_.cbdivw)
        .addField(_.cbdivx)
        .addField(_.cbdivy)
        .addField(_.cbdivz)    
        .tableName("C1PDBR")
        .singlePrimaryKey(_.cbdiv)
        .buildKeyedTableMapper
    
    
    class TableDsl(join: jdbcf.querydsl.QueryDsl.Join = jdbcf.querydsl.QueryDsl.RootJoin) {
      val cbdiv = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdiv", join)
      val cbdiva = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdiva", join)
      val cbdivb = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivb", join)
      val cbdivc = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivc", join)
      val cbdivd = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivd", join)
      val cbdive = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdive", join)
      val cbdivf = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivf", join)
      val cbdivg = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivg", join)
      val cbdivh = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivh", join)
      val cbdivi = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivi", join)
      val cbdivj = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivj", join)
      val cbdivk = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivk", join)
      val cbdivl = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivl", join)
      val cbdivm = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivm", join)
      val cbdivn = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivn", join)
      val cbdivo = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivo", join)
      val cbdivp = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivp", join)
      val cbdivq = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivq", join)
      val cbdivr = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivr", join)
      val cbdivs = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivs", join)
      val cbdivt = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivt", join)
      val cbdivu = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivu", join)
      val cbdivv = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivv", join)
      val cbdivw = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivw", join)
      val cbdivx = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivx", join)
      val cbdivy = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivy", join)
      val cbdivz = jdbcf.querydsl.QueryDsl.field[ClientId]("cbdivz", join)
    
    }
    
    val queryDsl = new jdbcf.querydsl.QueryDsl[ClientInfo, TableDsl, ClientId](jdbcMapper, new TableDsl)
    
    def query(whereFn: TableDsl => jdbcf.querydsl.QueryDsl.Condition): jdbcf.querydsl.SelectQuery[ClientInfo, TableDsl] =
      queryDsl.query(whereFn)
    
    def update(set: TableDsl => Iterable[jdbcf.querydsl.UpdateQuery.Assignment[?]]): jdbcf.querydsl.UpdateQuery[TableDsl] =
      queryDsl.update(set)
    
    
    
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[ClientInfo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[ClientInfo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[ClientInfo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.cbdiv)
          .addField(_.cbdiva)
          .addField(_.cbdivb)
          .addField(_.cbdivc)
          .addField(_.cbdivd)
          .addField(_.cbdive)
          .addField(_.cbdivf)
          .addField(_.cbdivg)
          .addField(_.cbdivh)
          .addField(_.cbdivi)
          .addField(_.cbdivj)
          .addField(_.cbdivk)
          .addField(_.cbdivl)
          .addField(_.cbdivm)
          .addField(_.cbdivn)
          .addField(_.cbdivo)
          .addField(_.cbdivp)
          .addField(_.cbdivq)
          .addField(_.cbdivr)
          .addField(_.cbdivs)
          .addField(_.cbdivt)
          .addField(_.cbdivu)
          .addField(_.cbdivv)
          .addField(_.cbdivw)
          .addField(_.cbdivx)
          .addField(_.cbdivy)
          .addField(_.cbdivz)
      )
      .build
    
    
    given scala.CanEqual[ClientInfo, ClientInfo] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[ClientInfo,parameters.type] =  {
      val constructors = Constructors[ClientInfo](27, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val cbdiv: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdiv", _.cbdiv, (d,v) => d.copy(cbdiv = v), None, 0)
      lazy val cbdiva: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdiva", _.cbdiva, (d,v) => d.copy(cbdiva = v), None, 1)
      lazy val cbdivb: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivb", _.cbdivb, (d,v) => d.copy(cbdivb = v), None, 2)
      lazy val cbdivc: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivc", _.cbdivc, (d,v) => d.copy(cbdivc = v), None, 3)
      lazy val cbdivd: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivd", _.cbdivd, (d,v) => d.copy(cbdivd = v), None, 4)
      lazy val cbdive: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdive", _.cbdive, (d,v) => d.copy(cbdive = v), None, 5)
      lazy val cbdivf: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivf", _.cbdivf, (d,v) => d.copy(cbdivf = v), None, 6)
      lazy val cbdivg: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivg", _.cbdivg, (d,v) => d.copy(cbdivg = v), None, 7)
      lazy val cbdivh: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivh", _.cbdivh, (d,v) => d.copy(cbdivh = v), None, 8)
      lazy val cbdivi: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivi", _.cbdivi, (d,v) => d.copy(cbdivi = v), None, 9)
      lazy val cbdivj: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivj", _.cbdivj, (d,v) => d.copy(cbdivj = v), None, 10)
      lazy val cbdivk: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivk", _.cbdivk, (d,v) => d.copy(cbdivk = v), None, 11)
      lazy val cbdivl: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivl", _.cbdivl, (d,v) => d.copy(cbdivl = v), None, 12)
      lazy val cbdivm: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivm", _.cbdivm, (d,v) => d.copy(cbdivm = v), None, 13)
      lazy val cbdivn: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivn", _.cbdivn, (d,v) => d.copy(cbdivn = v), None, 14)
      lazy val cbdivo: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivo", _.cbdivo, (d,v) => d.copy(cbdivo = v), None, 15)
      lazy val cbdivp: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivp", _.cbdivp, (d,v) => d.copy(cbdivp = v), None, 16)
      lazy val cbdivq: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivq", _.cbdivq, (d,v) => d.copy(cbdivq = v), None, 17)
      lazy val cbdivr: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivr", _.cbdivr, (d,v) => d.copy(cbdivr = v), None, 18)
      lazy val cbdivs: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivs", _.cbdivs, (d,v) => d.copy(cbdivs = v), None, 19)
      lazy val cbdivt: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivt", _.cbdivt, (d,v) => d.copy(cbdivt = v), None, 20)
      lazy val cbdivu: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivu", _.cbdivu, (d,v) => d.copy(cbdivu = v), None, 21)
      lazy val cbdivv: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivv", _.cbdivv, (d,v) => d.copy(cbdivv = v), None, 22)
      lazy val cbdivw: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivw", _.cbdivw, (d,v) => d.copy(cbdivw = v), None, 23)
      lazy val cbdivx: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivx", _.cbdivx, (d,v) => d.copy(cbdivx = v), None, 24)
      lazy val cbdivy: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivy", _.cbdivy, (d,v) => d.copy(cbdivy = v), None, 25)
      lazy val cbdivz: CaseClassParm[ClientInfo,ClientId] = CaseClassParm[ClientInfo,ClientId]("cbdivz", _.cbdivz, (d,v) => d.copy(cbdivz = v), None, 26)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): ClientInfo = {
        ClientInfo(
          cbdiv = values(0).asInstanceOf[ClientId],
          cbdiva = values(1).asInstanceOf[ClientId],
          cbdivb = values(2).asInstanceOf[ClientId],
          cbdivc = values(3).asInstanceOf[ClientId],
          cbdivd = values(4).asInstanceOf[ClientId],
          cbdive = values(5).asInstanceOf[ClientId],
          cbdivf = values(6).asInstanceOf[ClientId],
          cbdivg = values(7).asInstanceOf[ClientId],
          cbdivh = values(8).asInstanceOf[ClientId],
          cbdivi = values(9).asInstanceOf[ClientId],
          cbdivj = values(10).asInstanceOf[ClientId],
          cbdivk = values(11).asInstanceOf[ClientId],
          cbdivl = values(12).asInstanceOf[ClientId],
          cbdivm = values(13).asInstanceOf[ClientId],
          cbdivn = values(14).asInstanceOf[ClientId],
          cbdivo = values(15).asInstanceOf[ClientId],
          cbdivp = values(16).asInstanceOf[ClientId],
          cbdivq = values(17).asInstanceOf[ClientId],
          cbdivr = values(18).asInstanceOf[ClientId],
          cbdivs = values(19).asInstanceOf[ClientId],
          cbdivt = values(20).asInstanceOf[ClientId],
          cbdivu = values(21).asInstanceOf[ClientId],
          cbdivv = values(22).asInstanceOf[ClientId],
          cbdivw = values(23).asInstanceOf[ClientId],
          cbdivx = values(24).asInstanceOf[ClientId],
          cbdivy = values(25).asInstanceOf[ClientId],
          cbdivz = values(26).asInstanceOf[ClientId],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): ClientInfo = {
        val value =
          ClientInfo(
            cbdiv = values.next().asInstanceOf[ClientId],
            cbdiva = values.next().asInstanceOf[ClientId],
            cbdivb = values.next().asInstanceOf[ClientId],
            cbdivc = values.next().asInstanceOf[ClientId],
            cbdivd = values.next().asInstanceOf[ClientId],
            cbdive = values.next().asInstanceOf[ClientId],
            cbdivf = values.next().asInstanceOf[ClientId],
            cbdivg = values.next().asInstanceOf[ClientId],
            cbdivh = values.next().asInstanceOf[ClientId],
            cbdivi = values.next().asInstanceOf[ClientId],
            cbdivj = values.next().asInstanceOf[ClientId],
            cbdivk = values.next().asInstanceOf[ClientId],
            cbdivl = values.next().asInstanceOf[ClientId],
            cbdivm = values.next().asInstanceOf[ClientId],
            cbdivn = values.next().asInstanceOf[ClientId],
            cbdivo = values.next().asInstanceOf[ClientId],
            cbdivp = values.next().asInstanceOf[ClientId],
            cbdivq = values.next().asInstanceOf[ClientId],
            cbdivr = values.next().asInstanceOf[ClientId],
            cbdivs = values.next().asInstanceOf[ClientId],
            cbdivt = values.next().asInstanceOf[ClientId],
            cbdivu = values.next().asInstanceOf[ClientId],
            cbdivv = values.next().asInstanceOf[ClientId],
            cbdivw = values.next().asInstanceOf[ClientId],
            cbdivx = values.next().asInstanceOf[ClientId],
            cbdivy = values.next().asInstanceOf[ClientId],
            cbdivz = values.next().asInstanceOf[ClientId],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(cbdiv: ClientId, cbdiva: ClientId, cbdivb: ClientId, cbdivc: ClientId, cbdivd: ClientId, cbdive: ClientId, cbdivf: ClientId, cbdivg: ClientId, cbdivh: ClientId, cbdivi: ClientId, cbdivj: ClientId, cbdivk: ClientId, cbdivl: ClientId, cbdivm: ClientId, cbdivn: ClientId, cbdivo: ClientId, cbdivp: ClientId, cbdivq: ClientId, cbdivr: ClientId, cbdivs: ClientId, cbdivt: ClientId, cbdivu: ClientId, cbdivv: ClientId, cbdivw: ClientId, cbdivx: ClientId, cbdivy: ClientId, cbdivz: ClientId): ClientInfo =
        ClientInfo(cbdiv, cbdiva, cbdivb, cbdivc, cbdivd, cbdive, cbdivf, cbdivg, cbdivh, cbdivi, cbdivj, cbdivk, cbdivl, cbdivm, cbdivn, cbdivo, cbdivp, cbdivq, cbdivr, cbdivs, cbdivt, cbdivu, cbdivv, cbdivw, cbdivx, cbdivy, cbdivz)
    
    }
    
    
    lazy val typeName = "ClientInfo"
  
  }
  
  
  
  
  trait MxStagerConfig { self: StagerConfig.type =>
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[StagerConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[StagerConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[StagerConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.vmDatabaseUser)
          .addField(_.vmDatabasePassword)
          .addField(_.postgresStagingDb)
      )
      .build
    
    
    given scala.CanEqual[StagerConfig, StagerConfig] = scala.CanEqual.derived
    
    
    
    lazy val generator: Generator[StagerConfig,parameters.type] =  {
      val constructors = Constructors[StagerConfig](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val vmDatabaseUser: CaseClassParm[StagerConfig,String] = CaseClassParm[StagerConfig,String]("vmDatabaseUser", _.vmDatabaseUser, (d,v) => d.copy(vmDatabaseUser = v), None, 0)
      lazy val vmDatabasePassword: CaseClassParm[StagerConfig,Password] = CaseClassParm[StagerConfig,Password]("vmDatabasePassword", _.vmDatabasePassword, (d,v) => d.copy(vmDatabasePassword = v), None, 1)
      lazy val postgresStagingDb: CaseClassParm[StagerConfig,DatabaseConfig] = CaseClassParm[StagerConfig,DatabaseConfig]("postgresStagingDb", _.postgresStagingDb, (d,v) => d.copy(postgresStagingDb = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): StagerConfig = {
        StagerConfig(
          vmDatabaseUser = values(0).asInstanceOf[String],
          vmDatabasePassword = values(1).asInstanceOf[Password],
          postgresStagingDb = values(2).asInstanceOf[DatabaseConfig],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): StagerConfig = {
        val value =
          StagerConfig(
            vmDatabaseUser = values.next().asInstanceOf[String],
            vmDatabasePassword = values.next().asInstanceOf[Password],
            postgresStagingDb = values.next().asInstanceOf[DatabaseConfig],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(vmDatabaseUser: String, vmDatabasePassword: Password, postgresStagingDb: DatabaseConfig): StagerConfig =
        StagerConfig(vmDatabaseUser, vmDatabasePassword, postgresStagingDb)
    
    }
    
    
    lazy val typeName = "StagerConfig"
  
  }
}
