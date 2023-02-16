package a8.sync

import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

import java.time.{LocalDate, LocalDateTime, LocalTime, Month, Year}
import a8.shared.SharedImports.canEqual.given

class DateTimeUtilsTest extends AnyFunSuite {


  def assertEquals[A](expected: A, actual: A): Assertion =
    assertResult(expected)(actual)

  def parseTimeTest(input: String, expected: Option[LocalTime]): Assertion = {
    val actual = DateTimeUtils.parseTime(input)
    assertEquals(expected, actual)
  }

  test("parseTime: empty string") {
    parseTimeTest("", None)
  }

  test("parseTime: trim to empty string") {
    parseTimeTest(" ", None)
  }

  test("parseTime: not time string") {
    parseTimeTest("blah blah blah", None)
  }

  test("parseTime: different time format") {
    parseTimeTest("Can think of/find a good example for this", None)
  }

  test("parseTime: no am/pm") {
    parseTimeTest("01:01", None)
  }

  test("parseTime: hour out of range") {
    parseTimeTest("60:01 am", None)
  }

  test("parseTime: minute out of range") {
    parseTimeTest("01:60 am", None)
  }

  test("parseTime: mix 24hr and 12hr times") {
    parseTimeTest("13:59 pm", None)
  }

  test("parseTime: noon") {
    parseTimeTest("12:00 pm", Some(LocalTime.of(12,0)))
  }

  test("parseTime: midnight") {
    parseTimeTest("12:00 am", Some(LocalTime.of(0,0)))
  }

  test("parseTime: seconds included") {
    parseTimeTest("01:01:30 am", Some(LocalTime.of(1,1)))
  }

  test("parseTime: hour and minute less than 10") {
    parseTimeTest("01:01 am", Some(LocalTime.of(1,1,0)))
  }

  test("parseTime: hour missing leading zero") {
    parseTimeTest("1:01 am", Some(LocalTime.of(1,1,0)))
  }

  test("parseTime: hour and minute greater than 10") {
    parseTimeTest("11:11 am", Some(LocalTime.of(11,11,0)))
  }

  test("parseTime: leading and trailing spaces") {
    parseTimeTest("            11:11 am             ", Some(LocalTime.of(11,11,0)))
  }

  test("parseTime: extra internal space") {
    parseTimeTest("11:11   am", Some(LocalTime.of(11,11,0)))
  }

  test("parseTime: pm") {
    parseTimeTest("11:11 pm", Some(LocalTime.of(23,11,0)))
  }

  test("parseTime: latest time") {
    parseTimeTest("11:59 pm", Some(LocalTime.of(23,59,0)))
  }

  test("parseTime: earliest time") {
    parseTimeTest("00:00 am", Some(LocalTime.of(0,0,0)))
  }

  test("parseTime: capitalization") {
    parseTimeTest("10:10 AM", Some(LocalTime.of(10,10,0)))
  }

  test("parseTime: camel case") {
    parseTimeTest("10:10 Am", Some(LocalTime.of(10,10,0)))
  }


  def parseDateTest(input: String, expected: Option[LocalDate]): Assertion = {
    val actual = DateTimeUtils.parseDate(input)
    assertEquals(expected, actual)
  }

  test("parseDate: empty string") {
    parseDateTest("", None)
  }

  test("parseDate: trim to empty string") {
    parseDateTest(" ", None)
  }

  test("parseDate: not date string") {
    parseDateTest("bla bla ha", None)
  }

  test("parseDate: unsupported format") {
    parseDateTest("2021-05-03", None)
  }

  test("parseDate: year out of min range") {
    parseDateTest("10/01/-1000000000", None)
  }

  test("parseDate: year out of max range") {
    parseDateTest("10/01/1000000000", None)
  }

  test("parseDate: month out of min range 1") {
    parseDateTest("00/10/2021", None)
  }

  test("parseDate: month out of min range 2") {
    parseDateTest("0/10/2021", None)
  }

  test("parseDate: month out of max range ") {
    parseDateTest("13/10/2021", None)
  }

  test("parseDate: day out of min range 1") {
    parseDateTest("10/00/2021", None)
  }

  test("parseDate: day out of min range 2") {
    parseDateTest("10/0/2021", None)
  }

  test("parseDate: day out of max range") {
    parseDateTest("10/32/2021", None)
  }

  test("parseDate: min year") {
    parseDateTest("10/01/-999999999", Some(LocalDate.of(-999999999, Month.OCTOBER, 1)))
  }

  test("parseDate: max year") {
    parseDateTest("10/01/999999999", Some(LocalDate.of(999999999, Month.OCTOBER, 1)))
  }

  test("parseDate: less than 10 day/month with leading zero") {
    parseDateTest("01/01/2021", Some(LocalDate.of(2021, Month.JANUARY, 1)))
  }

  test("parseDate: less than 10 day/month without leading zero") {
    parseDateTest("1/1/2021", Some(LocalDate.of(2021, Month.JANUARY, 1)))
  }

  test("parseDate: greater than 10 day/month") {
    parseDateTest("11/11/2021", Some(LocalDate.of(2021, Month.NOVEMBER, 11)))
  }

  test("parseDate: leading and trailing spaces") {
    parseDateTest("              11/11/2021               ", Some(LocalDate.of(2021, Month.NOVEMBER, 11)))
  }

  test("parseDate: valid leap year") {
    parseDateTest("02/29/2016", Some(LocalDate.of(2016, Month.FEBRUARY, 29)))
  }

  test("parseDate: invalid leap year") {
    parseDateTest("02/30/2016", None)
  }

  def parseDateTimeTest(input: String, expected: Option[LocalDateTime]): Assertion = {
    val actual = DateTimeUtils.parseDateTime(input)
    assertEquals(expected, actual)
  }

  test("parseDateTime: empty string") {
    parseDateTimeTest("", None)
  }

  test("parseDateTime: trim to empty string") {
    parseDateTimeTest(" ", None)
  }

  test("parseDateTime: not dateTime string") {
    parseDateTimeTest("bla bla ha", None)
  }

  test("parseDateTime: unsupported format") {
    parseDateTimeTest("05-03-2021 01:01:30 am",  None)
  }

  test("parseDateTime: no am/pm") {
    parseDateTimeTest("10/10/1970 10:10", None)
  }

  test("parseDateTime: year out of min range") {
    parseDateTimeTest("10/01/-1000000000 10:10 am", None)
  }

  test("parseDateTime: year out of max range") {
    parseDateTimeTest("10/01/1000000000 10:10 am", None)
  }

  test("parseDateTime: month out of min range 1") {
    parseDateTimeTest("00/10/2021 10:10 am",  None)
  }

  test("parseDateTime: month out of min range 2") {
    parseDateTimeTest("0/10/2021 10:10 am",  None)
  }

  test("parseDateTime: month out of max range") {
    parseDateTimeTest("13/10/2021 10:10 am",  None)
  }

  test("parseDateTime: day out of min range 1") {
    parseDateTimeTest("10/0/2021 10:10 am", None)
  }

  test("parseDateTime: day out of min range 2") {
    parseDateTimeTest("10/00/2021 10:10 am", None)
  }

  test("parseDateTime: day out of max range") {
    parseDateTimeTest("10/32/2021 10:10 am", None)
  }

  test("parseDateTime: hour out of range") {
    parseDateTimeTest("10/10/2021 25:10 am", None)
  }

  test("parseDateTime: minute out of range") {
    parseDateTimeTest("10/10/2021 60:01 am", None)
  }

  test("parseDateTime: min year") {
    parseDateTimeTest(
      "10/01/-999999999 10:10 am",
      Some(LocalDateTime.of(-999999999, Month.OCTOBER, 1, 10, 10, 0))
    )
  }

  test("parseDateTime: max year") {
    parseDateTimeTest(
      "10/01/999999999 10:10 am",
      Some(LocalDateTime.of(999999999, Month.OCTOBER, 1, 10, 10, 0))
    )
  }

  test("parseDateTime: seconds included") {
    parseDateTimeTest(
      "05/03/2021 01:01:30 am",
      Some(LocalDateTime.of(2021, Month.MAY,3,1,1,0))
    )
  }

  test("parseDateTime: less than 10 month,day,hour/minute with leading zero") {
    parseDateTimeTest(
      "05/03/2021 01:01 am",
      Some(LocalDateTime.of(2021, Month.MAY,3,1,1,0))
    )
  }

  test("parseDateTime: less than 10 month,day,hour/minute without leading zero") {
    parseDateTimeTest(
      "5/3/2021 1:01 am",
      Some(LocalDateTime.of(2021, Month.MAY,3,1,1,0))
    )
  }

  test("parseDateTime: greater than or equal to 10 month,day,hour/minute") {
    parseDateTimeTest(
      "10/10/2021 10:10 am",
      Some(LocalDateTime.of(2021, Month.OCTOBER,10,10,10,0))
    )
  }

  test("parseDateTime: leading and trailing spaces") {
    parseDateTimeTest(
      "                10/10/2021 10:10 am                  ",
      Some(LocalDateTime.of(2021, Month.OCTOBER,10,10,10,0))
    )
  }

  test("parseDateTime: extra internal space") {
    parseDateTimeTest(
      "10/10/2021  10:10    am",
      Some(LocalDateTime.of(2021, Month.OCTOBER,10,10,10,0))
    )
  }

  test("parseDateTime: pm") {
    parseDateTimeTest(
      "10/10/2021 10:10 pm",
      Some(LocalDateTime.of(2021, Month.OCTOBER,10,22,10,0))
    )
  }

  test("parseDateTime: early date") {
    parseDateTimeTest(
      "10/10/1970 10:10 pm",
      Some(LocalDateTime.of(1970, Month.OCTOBER,10,22,10,0))
    )
  }

  test("parseDateTime: capitalization") {
    parseDateTimeTest(
      "10/10/1970 10:10 PM",
      Some(LocalDateTime.of(1970, Month.OCTOBER,10,22,10,0))
    )
  }

  test("parseDateTime: camel case") {
    parseDateTimeTest(
      "10/10/1970 10:10 PM",
      Some(LocalDateTime.of(1970, Month.OCTOBER,10,22,10,0))
    )
  }

  test("parseDateTime: min time") {
    parseDateTimeTest(
      "10/10/1970 00:00 am",
      Some(LocalDateTime.of(1970, Month.OCTOBER,10,0,0,0))
    )
  }

  test("parseDateTime: valid leap year") {
    parseDateTimeTest(
      "02/29/2016 01:01 am",
      Some(LocalDateTime.of(2016, Month.FEBRUARY,29,1,1,0))
    )
  }

  test("parseDateTime: invalid leap year") {
    parseDateTimeTest("02/30/2016 01:01 am", None)
  }

  def parseCyymmddDate(input: Int, expected: Option[LocalDate]): Assertion = {
    val actual = DateTimeUtils.parseCyymmddDate(input)
    assertEquals(expected, actual)
  }

  test("parseCyymmddDate: negative") {
    parseCyymmddDate(-1, None)
  }

  test("parseCyymmddDate: 0") {
    parseCyymmddDate(0, None)
  }

  test("parseCyymmddDate: 10/10/1999") {
    parseCyymmddDate(991010, Some(LocalDate.of(1999, Month.OCTOBER, 10)))
  }

  test("parseCyymmddDate: 09/09/1999") {
    parseCyymmddDate(990909, Some(LocalDate.of(1999, Month.SEPTEMBER, 9)))
  }

  test("parseCyymmddDate: 09/09/2000") {
    parseCyymmddDate(1000909, Some(LocalDate.of(2000, Month.SEPTEMBER, 9)))
  }

  test("parseCyymmddDate: 10/10/2001") {
    parseCyymmddDate(1011010, Some(LocalDate.of(2001, Month.OCTOBER, 10)))
  }

  test("parseCyymmddDate: 09/09/2001") {
    parseCyymmddDate(1010909, Some(LocalDate.of(2001, Month.SEPTEMBER, 9)))
  }

  test("parseCyymmddDate: leap year") {
    parseCyymmddDate(1160229, Some(LocalDate.of(2016, Month.FEBRUARY, 29)))
  }


}
