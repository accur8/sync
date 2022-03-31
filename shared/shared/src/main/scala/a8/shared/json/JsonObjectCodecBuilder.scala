package a8.shared.json

import a8.shared.Meta.{CaseClassParm, Generator}
import a8.shared.SharedImports._
import a8.shared.json.ast._

object JsonObjectCodecBuilder {

  sealed trait Parm[A] {
    val name: String
    def write(a: A): JsVal
    def read(jsonDoc: JsDoc): Either[ReadError,Any]
  }

  case class CaseClassParmParm[A,B](parm: CaseClassParm[A,B])(implicit jsonCodec: JsonCodec[B]) extends Parm[A] {

    override val name: String = parm.name

    override def write(a: A): JsVal =
      jsonCodec.write(parm.lens(a))

    override def read(jsonDoc: JsDoc): Either[ReadError,Any] =
      jsonCodec.read(jsonDoc, parm.default)

  }

  def apply[A,B](generator: Generator[A,B]): JsonObjectCodecBuilder[A,B] =
    JsonObjectCodecBuilderImpl(generator)


  case class JsonObjectCodecBuilderImpl[A,B](generator: Generator[A,B], parms: Vector[Parm[A]] = Vector.empty, ignoredFields: Vector[String] = Vector.empty) extends JsonObjectCodecBuilder[A,B] {

    override def addField[C : JsonCodec](fn: B => CaseClassParm[A, C]): JsonObjectCodecBuilder[A, B] =
      copy(parms = parms :+ CaseClassParmParm(fn(generator.caseClassParameters)))

    override def build: JsonObjectCodec[A] =
      new JsonObjectCodec(parms, generator.constructors, ignoredFields)

    override def addIgnoredField(name: String): JsonObjectCodecBuilder[A, B] =
      copy(ignoredFields = ignoredFields :+ name)

  }

}


trait JsonObjectCodecBuilder[A,B] {
  def addField[C : JsonCodec](fn: B => CaseClassParm[A,C]): JsonObjectCodecBuilder[A,B]
  def addIgnoredField(name: String): JsonObjectCodecBuilder[A,B]
  def build: JsonTypedCodec[A,JsObj]
}
