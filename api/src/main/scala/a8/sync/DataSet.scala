package a8.sync

import a8.shared.jdbcf.Row
import org.typelevel.ci.CIString

object DataSet {

}


case class DataSet(
  rows: Vector[Row]
)

