package a8.sync

import a8.sync.Imports.ParseInt

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.util.Try
import a8.sync.Imports._

object DateTimeUtils {

  object TimeAnteMeridiem extends Enumeration {
    type AnteMeridiem = Value
    val AM: Value = Value(0, "am")
    val PM: Value = Value(12, "pm")

    def unapply(s: String): Option[TimeAnteMeridiem.Value] =
      s
        .trim
        .toLowerCase match {
          case "am" => Some(AM)
          case "pm" => Some(PM)
          case _ => None
        }

  }

  // MM/dd/yyyy
  def parseDate(dateStr: String): Option[LocalDate] =
    dateStr
      .trim
      .splitList("/") match {
        case List(ParseInt(mm), ParseInt(dd), ParseInt(ccyy)) =>
          Try(
            LocalDate.of(ccyy, mm, dd)
          ).toOption
        case _ =>
          None
      }

  // hh:mm a
  def parseTime(timeStr: String): Option[LocalTime] = {

    def tryTime(hh: Int, mm: Int, amPm: Option[TimeAnteMeridiem.Value]): Option[LocalTime] =
      amPm
        .flatMap { v =>
          val hour = hh match {
            case 12 => v.id
            case o => o + v.id
          }
          Try(
            LocalTime.of(hour, mm)
          ).toOption
        }


    timeStr
      .trim
      .splitList(" ") match {
        case List(hhmm, amPmStr) => {
          val amPm: Option[TimeAnteMeridiem.Value] = TimeAnteMeridiem.unapply(amPmStr)
          hhmm
            .splitList(":") match {
              case List(ParseInt(hh), ParseInt(mm)) =>
                tryTime(hh, mm, amPm)
              case List(ParseInt(hh), ParseInt(mm), ParseInt(_)) =>
                tryTime(hh, mm, amPm)
              case _ =>
                None
            }
        }
        case List("0") =>
          // TODO: DXC sometimes has "0" instead of a time. This needs to be reviewed to see if that is valid.
          Some(LocalTime.of(0, 0))
        case _ =>
          None
      }
  }

  // MM/dd/yyyy hh:mm a
  def parseDateTime(dateTimeStr: String): Option[LocalDateTime] =
    dateTimeStr
      .trim
      .splitList(" ") match {
        case List(mmddyyyy, hhmm, a) =>
          (parseDate(mmddyyyy), parseTime(s"${hhmm} ${a}")) match {
            case (Some(date), Some(time)) =>
              Try(
                LocalDateTime.of(date, time)
              ).toOption
            case _ =>
              None
          }
        case _ =>
          None
      }

  def parseCyymmddDate(i: Int): Option[LocalDate] =
    Try {
      val year = i / 10000 + 1900
      val month = i / 100 % 100
      val day = i % 100
      LocalDate.of(year, month, day)
    }.toOption

}
