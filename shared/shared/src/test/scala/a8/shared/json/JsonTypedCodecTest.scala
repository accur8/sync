package a8.shared.json

import org.scalatest.funsuite.AnyFunSuite
import JsonTypedCodec._
import a8.shared.json.ast._

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, OffsetTime, ZoneOffset, ZonedDateTime}
import java.util.UUID
import scala.concurrent.duration._

class JsonTypedCodecTest extends AnyFunSuite {

  //todo: need to have tests for:
  // - Boolean
  // - Uri
  // - String
  // - Long
  // - BigDecimal
  // - Int
  // - Short
  // - JsDoc
  // - JsObj
  // - JsArr

  test("Write java.sql.Timestamp") {
    val input = java.sql.Timestamp.valueOf("2022-10-15 20:40:50.123456789")
    val expected = JsStr("2022-10-15 20:40:50.123456789")
    val actual = JsonCodec[java.sql.Timestamp].write(input)
    assert(actual == expected)
  }

  test("Read java.sql.Timestamp") {
    val input = JsStr("2022-10-15 20:40:50.123456789")
    val expected = Right(java.sql.Timestamp.valueOf("2022-10-15 20:40:50.123456789"))
    val actual = input.as[java.sql.Timestamp]
    assert(actual == expected)
  }

  test("Write LocalDate") {
    val input = LocalDate.of(2022, 10, 15)
    val expected = JsStr("2022-10-15")
    val actual = JsonCodec[LocalDate].write(input)
    assert(actual == expected)
  }

  test("Read LocalDate") {
    val input = JsStr("2022-10-15")
    val expected = Right(LocalDate.of(2022, 10, 15))
    val actual = input.as[LocalDate]
    assert(actual == expected)
  }

  test("Write LocalDateTime") {
    val input = LocalDateTime.of(2022, 10, 15, 20, 40, 50, 123456789)
    val expected = JsStr("2022-10-15T20:40:50.123456789")
    val actual = JsonCodec[LocalDateTime].write(input)
    assert(actual == expected)
  }

  test("Read LocalDateTime") {
    val input = JsStr("2022-10-15T20:40:50.123456789")
    val expected = Right(LocalDateTime.of(2022, 10, 15, 20, 40, 50, 123456789))
    val actual = input.as[LocalDateTime]
    assert(actual == expected)
  }

  test("Write LocalTime") {
    val input = LocalTime.of(20, 40, 50, 123456789)
    val expected = JsStr("20:40:50.123456789")
    val actual = JsonCodec[LocalTime].write(input)
    assert(actual == expected)
  }

  test("Read LocalTime") {
    val input = JsStr("20:40:50.123456789")
    val expected = Right(LocalTime.of(20, 40, 50, 123456789))
    val actual = input.as[LocalTime]
    assert(actual == expected)
  }

  test("Write OffsetDateTime") {
    val input = OffsetDateTime.of(2022, 10, 15, 20, 40, 50, 123456789, ZoneOffset.ofHours(-5))
    val expected = JsStr("2022-10-15T20:40:50.123456789-05:00")
    val actual = JsonCodec[OffsetDateTime].write(input)
    assert(actual == expected)
  }

  test("Read OffsetDateTime") {
    val input = JsStr("2022-10-15T20:40:50.123456789-05:00")
    val expected = Right(OffsetDateTime.of(2022, 10, 15, 20, 40, 50, 123456789, ZoneOffset.ofHours(-5)))
    val actual = input.as[OffsetDateTime]
    assert(actual == expected)
  }

  test("Write OffsetTime") {
    val input = OffsetTime.of(20, 40, 50, 123456789, ZoneOffset.ofHours(-5))
    val expected = JsStr("20:40:50.123456789-05:00")
    val actual = JsonCodec[OffsetTime].write(input)
    assert(actual == expected)
  }

  test("Read OffsetTime") {
    val input = JsStr("20:40:50.123456789-05:00")
    val expected = Right(OffsetTime.of(20, 40, 50, 123456789, ZoneOffset.ofHours(-5)))
    val actual = input.as[OffsetTime]
    assert(actual == expected)
  }

  test("Write ZonedDateTime") {
    val input = ZonedDateTime.of(2022, 10, 15, 20, 40, 50, 123456789, ZoneOffset.ofHours(-5))
    val expected = JsStr("2022-10-15T20:40:50.123456789-05:00")
    val actual = JsonCodec[ZonedDateTime].write(input)
    assert(actual == expected)
  }

  test("Read ZonedDateTime") {
    val input = JsStr("2022-10-15T20:40:50.123456789-05:00")
    val expected = Right(ZonedDateTime.of(2022, 10, 15, 20, 40, 50, 123456789, ZoneOffset.ofHours(-5)))
    val actual = input.as[ZonedDateTime]
    assert(actual == expected)
  }

  test("Write Instant") {
    val input = Instant.parse("2022-10-15T14:28:45.123456789Z")
    val expected = JsStr("2022-10-15T14:28:45.123456789Z")
    val actual = JsonCodec[Instant].write(input)
    assert(actual == expected)
  }

  test("Read Instant") {
    val input = JsStr("2022-10-15T14:28:45.123456789Z")
    val expected = Right(Instant.parse("2022-10-15T14:28:45.123456789Z"))
    val actual = input.as[Instant]
    assert(actual == expected)
  }

  test("Write FiniteDuration") {
    val input = 10.minutes
    val expected = JsStr("10 MINUTES")
    val actual = JsonCodec[FiniteDuration].write(input)
    assert(actual == expected)
  }

  test("Read FiniteDuration") {
    val input = JsStr("30 seconds")
    val expected = Right(30.seconds)
    val actual = input.as[FiniteDuration]
    assert(actual == expected)
  }

  test("Write UUID") {
    val uuidStr = "b4bd7d4c-eea1-4f67-88a8-83046a12bb24"
    val input = UUID.fromString(uuidStr)
    val expected = JsStr(uuidStr)
    val actual = JsonCodec[UUID].write(input)
    assert(actual == expected)
  }

  test("Read UUID") {
    val uuidStr = "b4bd7d4c-eea1-4f67-88a8-83046a12bb24"
    val input = JsStr(uuidStr)
    val expected = Right(UUID.fromString(uuidStr))
    val actual = input.as[UUID]
    assert(actual == expected)
  }

  test("Write java.nio.file.Path") {
    val input = java.nio.file.Path.of("opt", "apps", "qubes", "config", "config.json")
    val expected = JsStr(input.toAbsolutePath.toString)
    val actual = JsonCodec[java.nio.file.Path].write(input)
    assert(actual == expected)
  }

  test("Read java.nio.file.Path") {
    val input = JsStr("opt/apps/qubes/config/config.json")
    val expected = Right(java.nio.file.Path.of("opt", "apps", "qubes", "config", "config.json"))
    val actual = input.as[java.nio.file.Path]
    assert(actual == expected)
  }

}
