package a8.shared.json

import a8.shared.Meta.{CaseClassParm, Generator}
import a8.shared.SharedImports._
import a8.shared.json.ast._

import java.util.regex.Pattern
import scala.util.matching.Regex

object JsonObjectCodecBuilder {

  sealed trait Parm[A] {
    val name: String
    def write(a: A): JsVal
    def read(jsonDoc: JsDoc): Either[ReadError,Any]
    def addAliases(aliases: Iterable[String]): Parm[A]
    val extraAliases: Iterable[String]
    lazy val resolvedAliases = name.some ++ extraAliases.filter(_ != name)
  }

  case class CaseClassParmParm[A,B](parm: CaseClassParm[A,B], extraAliases: Iterable[String] = Iterable.empty)(implicit jsonCodec: JsonCodec[B]) extends Parm[A] {

    override val name: String = parm.name

    override def write(a: A): JsVal =
      jsonCodec.write(parm.lens(a))

    override def read(jsonDoc: JsDoc): Either[ReadError,Any] =
      jsonCodec.read(jsonDoc, parm.default)

    override def addAliases(aliases: Iterable[String]): Parm[A] =
      copy(extraAliases = this.extraAliases ++ aliases)

  }

  def apply[A,B](generator: Generator[A,B]): JsonObjectCodecBuilder[A,B] =
    JsonObjectCodecBuilderImpl(generator)


  sealed trait IgnoredField {
    def ignore(fieldName: String): Boolean
  }
  object IgnoredField {
    case class IgnoreFieldString(value: String) extends IgnoredField {
      override def ignore(fieldName: String): Boolean = value === fieldName
    }
    case class IgnoreFieldRegex(regex: Regex) extends IgnoredField {
      override def ignore(fieldName: String): Boolean =
        regex.matches(fieldName)
    }
  }

  case class JsonObjectCodecBuilderImpl[A,B](
    generator: Generator[A,B],
    parms: Vector[Parm[A]] = Vector.empty,
    ignoredFields: Vector[IgnoredField] = Vector.empty,
    aliases: Vector[(String,String)] = Vector.empty,
  ) extends JsonObjectCodecBuilder[A,B] {

    override def removeField(fieldName: String): JsonObjectCodecBuilder[A, B] =
      copy(parms = parms.filter(_.name != fieldName))

    override def addAlias(existingFieldName: String, alias: String): JsonObjectCodecBuilder[A, B] =
      copy(aliases = aliases :+ (existingFieldName -> alias))

    override def addIgnoredFieldRegex(regex: Regex): JsonObjectCodecBuilder[A, B] =
      copy(ignoredFields = ignoredFields :+ IgnoredField.IgnoreFieldRegex(regex))

    override def addField[C : JsonCodec](fn: B => CaseClassParm[A, C]): JsonObjectCodecBuilder[A, B] =
      copy(parms = parms :+ CaseClassParmParm(fn(generator.caseClassParameters)))

    override def build: JsonObjectCodec[A] =
      new JsonObjectCodec(parms, generator.constructors, ignoredFields, aliases)

    override def addIgnoredField(name: String): JsonObjectCodecBuilder[A, B] =
      copy(ignoredFields = ignoredFields :+ IgnoredField.IgnoreFieldString(name))

  }

}


trait JsonObjectCodecBuilder[A,B] {
  def addField[C : JsonCodec](fn: B => CaseClassParm[A,C]): JsonObjectCodecBuilder[A,B]
  def removeField(fieldName: String): JsonObjectCodecBuilder[A,B]
  def addAlias(existingFieldName: String, alias: String): JsonObjectCodecBuilder[A,B]
  def addIgnoredField(name: String): JsonObjectCodecBuilder[A,B]
  def addIgnoredFieldRegex(regex: Regex): JsonObjectCodecBuilder[A,B]
  def build: JsonTypedCodec[A,JsObj]
}
