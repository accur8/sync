package a8.shared.json


import a8.shared.{Chord, FileSystem, SingleArgConstructor}
import a8.shared.json.ast._
import a8.shared.json.impl.{JsValOps, JsonCodecs}

import scala.reflect.{ClassTag, classTag}
import a8.shared.SharedImports._
import a8.shared.json.JsonReadOptions.UnusedFieldAction

import scala.collection.mutable

/**
 * Companion object for JsonCodec providing factory methods and implicit conversions.
 * 
 * Extends JsonCodecs to provide built-in codec instances for common types.
 * 
 * @example {{{
 * // Using automatic derivation for case classes
 * case class User(id: Long, name: String, email: String)
 * implicit val userCodec: JsonCodec[User] = JsonCodec.caseCodec[User]
 * 
 * // Using the codec
 * val user = User(1, "Alice", "alice@example.com")
 * val json = user.toJsVal
 * val jsonString = user.prettyJson
 * }}}
 */
object JsonCodec extends JsonCodecs {

  // these are here temporarily to keep code compiling while we decide whether JsonTypedCodec is moved to a top level Api or stays hidden in impl
  val string = JsonTypedCodec.string
  val long = JsonTypedCodec.long

  @inline
  final def apply[A : JsonCodec]: JsonCodec[A] = implicitly[JsonCodec[A]]

  /**
   * Extension methods for any type that has a JsonCodec instance.
   * 
   * Provides convenient methods to convert values to JSON.
   */
  class JsonCodecOps[A: JsonCodec](private val a: A) {
    def toJsVal: JsVal = JsonCodec[A].write(a)
    def toJsRootDoc: JsDoc = toJsVal.toRootDoc
    def compactJson: String = JsValOps.toCompactJsonChord(toJsVal, false).toString
    def prettyJson: String = JsValOps.toPrettyJsonChord(toJsVal).toString
  }


  implicit def jsonTypedCodecAsJsonCodec[A, B <: JsVal](implicit jsonTypedCodec: JsonTypedCodec[A,B]): JsonCodec[A] =
    jsonTypedCodec.asJsonCodec

  def or[A](left: JsonCodec[A], right: JsonCodec[A]): JsonCodec[A] =
    new JsonCodec[A] {

      override def write(a: A): JsVal =
        left.write(a)

      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] =
        left.read(doc) match {
          case l@ Left(_) =>
            right.read(doc) match {
              case Left(_) =>
                l
              case r@ Right(_) =>
                r
            }
          case r@ Right(_) =>
            r
        }

    }

}

/**
 * Type class for JSON serialization and deserialization.
 * 
 * A JsonCodec[A] provides bidirectional conversion between Scala values of type A
 * and JSON representation (JsVal).
 * 
 * @tparam A The Scala type this codec can encode/decode
 * 
 * @example {{{
 * // Define a custom codec
 * implicit val dateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
 *   def write(date: LocalDate): JsVal = JsStr(date.toString)
 *   
 *   def read(doc: JsDoc)(implicit options: JsonReadOptions): Either[ReadError, LocalDate] =
 *     doc.as[JsStr].map(str => LocalDate.parse(str.value))
 * }
 * 
 * // Use with automatic codec for case classes
 * case class Event(name: String, date: LocalDate)
 * implicit val eventCodec: JsonCodec[Event] = JsonCodec.caseCodec[Event]
 * }}}
 */
trait JsonCodec[A] { jsonCodecA =>

  /**
   * Encode a value of type A to JSON representation.
   */
  def write(a: A): JsVal
  
  /**
   * Decode a JSON document to a value of type A.
   * 
   * @param doc The JSON document to decode
   * @param readOptions Options controlling the decoding behavior (e.g., handling of extra fields)
   * @return Either a ReadError (left) or the decoded value (right)
   */
  def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError,A]

  def read(doc: JsDoc, default: Option[() => A])(implicit readOptions: JsonReadOptions): Either[ReadError,A] = {
    doc.value match {
      case JsNothing =>
        default match {
          case None =>
            read(doc)
          case Some(fn) =>
            Right(fn())
        }
      case _ =>
        read(doc)
    }

  }

}
