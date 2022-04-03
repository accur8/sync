package a8.shared.jdbcf.mapper

import a8.shared.jdbcf.{RowReader, RowWriter}

trait Mapper[A] extends RowWriter[A] with RowReader[A] {

}
