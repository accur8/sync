package a8.shared.jdbcf

import java.time.LocalDate
import RowReader._
import sttp.model.Uri

import java.util.Calendar

trait MoreRowReaderCodecs {

  implicit val localDate: RowReader[LocalDate] =
    singleColumnReader[LocalDate] {
      case sd: java.sql.Timestamp =>
        sd.toLocalDateTime.toLocalDate
      case sd: java.sql.Date =>
        sd.toLocalDate
    }

  implicit val uri: RowReader[Uri] =
    RowReader.stringReader.map[Uri](Uri.unsafeParse)

}
