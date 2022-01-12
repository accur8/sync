package a8.sync.demos

import a8.shared.jdbcf

import java.sql.DriverManager
import com.ibm.as400.access.AS400JDBCDriver
import a8.shared.SharedImports._

object SimpleJdbc extends App {

  new AS400JDBCDriver

  val conn = DriverManager.getConnection("jdbc:as400://localhost", "gmarches", "mwrt3967")
//    val conn = DriverManager.getConnection("jdbc:as400://dev400.goodville.com?proxy server=goodville.vpn.accur8.io:3470", "gmarches", "mwrt3967")

  //  conn.createStatement().executeQuery("select * from PPITGDATA.CLPT006")

//  val rs = conn.getMetaData.getColumns(null, "PPITGDATA", "CLPT006", null)
//  val rs = conn.getMetaData.getPrimaryKeys(null, "PPITGDATA", "CLPT006")

  val rs =
    conn.createStatement.executeQuery("""
select * from table(key_list('PPITGDATA','CLPT006'))
  """)

  val l = jdbcf.resultSetToVector(rs)

  l.foreach(row => println(row.unsafeAsJsObj.compactJson))


  println(l)

}
