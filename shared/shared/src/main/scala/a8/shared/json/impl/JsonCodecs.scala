package a8.shared.json.impl

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import a8.shared.SharedImports._
import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.ast._
import a8.shared.json.impl.JsonCodecs.IterableJsonCodec
import a8.shared.json.{JsonCodec, JsonReadOptions, ReadError, ast}
import sttp.model.Uri

import scala.concurrent.duration.FiniteDuration
import scala.reflect.{ClassTag, classTag}

object JsonCodecs {

  case class IterableJsonCodec[A[_], B : JsonCodec](toIteratorFn: A[B] => Iterator[B], fromIteratorFn: Iterator[B] => A[B]) extends JsonCodec[A[B]] {

    override def write(list: A[B]): JsVal =
      JsArr(
        toIteratorFn(list)
          .map(b => JsonCodec[B].write(b))
          .toList
      )

    override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A[B]] =
      doc.value match {
        case JsArr(list) =>
          val result =
            fromIteratorFn(
              (0 until list.size)
                .iterator
                .map { i =>
                  JsonCodec[B].read(doc(i)) match {
                    case Left(re) =>
                      throw re.asException
                    case Right(v) =>
                      v
                  }
                }
            )
          Right(result)

        case _ =>
          doc.errorL("expected an array")
      }
  }

}

trait JsonCodecs {

  implicit def option[A : JsonCodec]: JsonCodec[Option[A]] =
    new JsonCodec[Option[A]] {
      override def write(a: Option[A]): JsVal =
        a match {
          case None =>
            JsNothing
          case Some(a) =>
            JsonCodec[A].write(a)
        }

      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, Option[A]] =
        doc.value match {
          case JsNothing | JsNull =>
            Right(None)
          case _ =>
            JsonCodec[A]
              .read(doc)
              .map(Some.apply)
        }

    }

  implicit def list[A : JsonCodec]: JsonCodec[List[A]] =
    IterableJsonCodec[List,A](_.iterator, _.toList)

  implicit def vector[A : JsonCodec]: JsonCodec[Vector[A]] =
    IterableJsonCodec[Vector,A](_.iterator, _.toVector)

  implicit def iterable[A : JsonCodec]: JsonCodec[Iterable[A]] =
    IterableJsonCodec[Iterable,A](_.iterator, _.to(Iterable))

  implicit def tuple2[A : JsonCodec, B : JsonCodec]: JsonCodec[(A,B)] =
    new JsonCodec[(A, B)] {

      val jsonCodecA = implicitly[JsonCodec[A]]
      val jsonCodecB = implicitly[JsonCodec[B]]

      override def write(t: (A, B)): JsVal =
        ast.JsArr(List(t._1.toJsVal, t._2.toJsVal))

      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, (A, B)] = {
        jsonCodecA
          .read(doc(0))
          .flatMap { a =>
            jsonCodecB.read(doc(1)).map(a -> _)
          }
      }
    }

  implicit def map[A : JsonCodec]: JsonCodec[Map[String,A]] =
    new JsonCodec[Map[String, A]] {

      val codecA = JsonCodec[A]

      override def write(map: Map[String, A]): JsVal =
        JsObj(
          map.map(t => t._1 -> codecA.write(t._2))
        )

      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, Map[String, A]] = {
        doc.value match {
          case jso: JsObj =>
            try {
              Right(
                jso
                  .values
                  .iterator
                  .map { t =>
                    val value =
                      codecA.read(JsDoc.JsDocRoot(t._2)) match {
                        case Right(v) =>
                          v
                        case Left(re) =>
                          throw re.asException
                      }
                    t._1 -> value
                  }
                  .toMap
              )
            } catch {
              case ree: ReadErrorException =>
                Left(ree.readError)
            }
          case _ =>
            doc.errorL("expected a json object")
        }
      }


    }

}