package a8.shared.json.impl

import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.{JsonCodec, JsonTypedCodec, ReadError, ast}
import a8.shared.json.ast.{JsArr, JsBool, JsDoc, JsFalse, JsNothing, JsNull, JsNum, JsObj, JsStr, JsTrue, JsVal}
import a8.shared.json.impl.JsonCodecs.IterableJsonCodec
import sttp.model.Uri

import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.reflect.{ClassTag, classTag}
import a8.shared.SharedImports._

import java.nio.file.Paths
import scala.util.Try

trait JsonTypedCodecs {

  def create[A : ClassTag, B <: JsVal : JsTypeInfo](
    writeFn: A => B,
  )(
    readFn: B => A,
  ): JsonTypedCodec[A,B] =
    new JsonTypedCodec[A,B] {

      val typeInfo = implicitly[JsTypeInfo[B]]
      val shortName = classTag[A].runtimeClass.shortName

      override def read(doc: JsDoc): Either[ReadError, A] =
        typeInfo
          .cast(doc.value)
          .map(b => Right(readFn(b)))
          .getOrElse(
            doc.errorL(s"expected ${typeInfo.name}")
          )

      override def write(a: A): B =
        writeFn(a)

    }

  implicit lazy val bool =
    create[Boolean,JsBool](
      b => if (b) JsTrue else JsFalse,
    ) {
      case JsTrue => true
      case JsFalse => false
    }

  implicit lazy val uri =
    string.dimap[Uri](
      Uri.unsafeParse,
      _.toString
    )

  implicit lazy val string: JsonTypedCodec[String,JsStr] =
    create[String,JsStr](
      s => JsStr(s)
    ) {
      case JsStr(s) => s
    }

  implicit lazy val long =
    bigDecimal.dimap2[Long](
      _.toLongExact,
      l => BigDecimal(l),
    )

  implicit object bigDecimal extends JsonTypedCodec[BigDecimal,JsNum] { outer =>

      val typeInfo = implicitly[JsTypeInfo[JsNum]]
      val shortName = classTag[Long].runtimeClass.shortName

      override def read(doc: JsDoc): Either[ReadError, BigDecimal] =
        doc.value match {
          case JsNum(bd) =>
            Right(bd)
          case JsStr(s) =>
            try {
              Right(BigDecimal(s))
            } catch {
              case IsNonFatal(_) =>
                doc.errorL(s"expected ${typeInfo.name}")
            }
          case _ =>
            doc.errorL(s"expected ${typeInfo.name}")
        }

      override def write(a: BigDecimal): JsNum =
        JsNum(a)

      /**
        * a hardened map method the gives good error messages for precision issues
        * when handling numeric types
        */
      def dimap2[A : ClassTag](
        toA: BigDecimal=>A,
        toBigDecimal: A=>BigDecimal,
      ): JsonTypedCodec[A,JsNum] =
        new JsonTypedCodec[A,JsNum] {

          val shortName = classTag[A].runtimeClass.shortName

          override def write(a: A): JsNum =
            JsNum(toBigDecimal(a))

          override def read(doc: JsDoc): Either[ReadError, A] =
            outer
              .read(doc)
              .flatMap { bd =>
                try {
                  Right(toA(bd))
                } catch {
                  case IsNonFatal(_) =>
                    doc.errorL(s"cannot convert ${bd} to a ${shortName}")
                }
              }

        }

    }

  implicit lazy val int =
    bigDecimal.dimap2[Int](
      _.toIntExact,
      v => BigDecimal(v),
    )

  implicit lazy val short =
    bigDecimal.dimap2[Short](
      _.toShortExact,
      v => BigDecimal(v),
    )

  implicit lazy val jsDoc: JsonTypedCodec[JsDoc,JsDoc] =
    new JsonTypedCodec[JsDoc,JsDoc] {
      override def write(a: JsDoc): JsDoc = a
      override def read(doc: JsDoc): Either[ReadError, JsDoc] = Right(doc)
    }

  implicit lazy val jobject =
    create[JsObj,JsObj](
      jo => jo
    ) {
      case jo: JsObj =>
        jo
    }

  implicit lazy val JsArr =
    create[JsArr,JsArr](
      ja => ja
    ) {
      case ja: JsArr =>
        ja
    }

  implicit lazy val jsqlTimestamp =
    string.dimap[java.sql.Timestamp](
      s => java.sql.Timestamp.valueOf(s.replace("T", " ")),
      _.toString,
    )

  implicit lazy val localDate =
    string.dimap[LocalDate](
      LocalDate.parse,
      _.toString,
    )

  implicit lazy val localDateTime =
    string.dimap[LocalDateTime](
      s => LocalDateTime.parse(s),
      _.toString,
    )

  implicit lazy val localTime =
    string.dimap[LocalTime](
      s => LocalTime.parse(s),
      _.toString,
    )

  implicit lazy val finiteDurationCodec: JsonTypedCodec[FiniteDuration,JsStr] = {
    val timeUnitsByName = TimeUnit.values().map(v => v.name().toLowerCase -> v).toMap
    def stringToValue(str: String): FiniteDuration = {
      str.trim.splitList(" ") match {
        case List(ParseLong(length)) =>
          FiniteDuration(length, TimeUnit.MILLISECONDS)
        case List(ParseLong(length), ParseTimeUnit(unit)) =>
          FiniteDuration(length, unit)
        case _ =>
          sys.error(s"unable to parse ${str} to a FiniteDuration")
      }
    }

    def valueToString(d: FiniteDuration): String = {
      if (d.unit == TimeUnit.MILLISECONDS) {
        d.toMillis.toString
      } else {
        s"${d.length} ${d.unit.name}"
      }
    }

    string.dimap[FiniteDuration](
      stringToValue,
      valueToString,
    )
  }

  implicit lazy val uuid =
    string.dimap[UUID](
      UUID.fromString,
      _.toString,
    )

  implicit lazy val nioPath =
    string.dimap[java.nio.file.Path](
      s => Paths.get(s),
      _.toFile.getCanonicalPath,
    )

}
