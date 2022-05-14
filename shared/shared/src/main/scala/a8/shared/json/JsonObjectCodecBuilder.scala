package a8.shared.json

import a8.shared.Meta.{CaseClassParm, Generator}
import a8.shared.SharedImports._
import a8.shared.json.ast._

import java.util.regex.Pattern
import scala.reflect.ClassTag
import scala.util.matching.Regex

object JsonObjectCodecBuilder {

  sealed trait Parm[A] {
    val name: String
    def write(a: A): JsVal
    def read(jsonDoc: JsDoc): Either[ReadError,Any]
    def addAliases(aliases: Iterable[String]): Parm[A]
    val extraAliases: Iterable[String]
    lazy val resolvedAliases = name.toSome ++ extraAliases.filter(_ != name)
    val ordinal: Int
  }

  case class CaseClassParmParm[A,B](parm: CaseClassParm[A,B], extraAliases: Iterable[String] = Iterable.empty)(implicit jsonCodec: JsonCodec[B]) extends Parm[A] {

    override val ordinal: Int = parm.ordinal

    override val name: String = parm.name

    override def write(a: A): JsVal =
      jsonCodec.write(parm.lens(a))

    override def read(jsonDoc: JsDoc): Either[ReadError,Any] =
      jsonCodec.read(jsonDoc, parm.default)

    override def addAliases(aliases: Iterable[String]): Parm[A] =
      copy(extraAliases = this.extraAliases ++ aliases)

  }

  def apply[A : ClassTag,B](generator: Generator[A,B]): JsonObjectCodecBuilder[A,B] =
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

  case class JsonObjectCodecBuilderImpl[A : ClassTag,B](
    generator: Generator[A,B],
    parms: Vector[Parm[A]] = Vector.empty,
    ignoredFields: Vector[IgnoredField] = Vector.empty,
    aliases: Vector[(String,String)] = Vector.empty,
  ) extends JsonObjectCodecBuilder[A,B] {

    override def addAlias[C](fn: B => CaseClassParm[A, C], alias: String): JsonObjectCodecBuilder[A, B] =
      copy(aliases = aliases :+ (fn(generator.caseClassParameters).name -> alias))

    override def addIgnoredFieldRegex(regex: Regex): JsonObjectCodecBuilder[A, B] =
      copy(ignoredFields = ignoredFields :+ IgnoredField.IgnoreFieldRegex(regex))

    override def addField[C : JsonCodec](fn: B => CaseClassParm[A, C]): JsonObjectCodecBuilder[A, B] =
      copy(parms = parms :+ CaseClassParmParm(fn(generator.caseClassParameters)))

    override def updateField[C : JsonCodec](fn: B => CaseClassParm[A, C]): JsonObjectCodecBuilder[A, B] = {
      val caseClassParm = fn(generator.caseClassParameters)
      copy(parms = parms.filter(_.name != caseClassParm.name) :+ CaseClassParmParm(caseClassParm))
    }

    override def build: JsonObjectCodec[A] =
      new JsonObjectCodec(parms, generator.constructors, ignoredFields, aliases, implicitly[ClassTag[A]])

    override def addIgnoredField(name: String): JsonObjectCodecBuilder[A, B] =
      copy(ignoredFields = ignoredFields :+ IgnoredField.IgnoreFieldString(name))

  }

}


trait JsonObjectCodecBuilder[A,B] {
  def updateField[C : JsonCodec](fn: B => CaseClassParm[A,C]): JsonObjectCodecBuilder[A,B]
  def addField[C : JsonCodec](fn: B => CaseClassParm[A,C]): JsonObjectCodecBuilder[A,B]
  def addAlias[C](fn: B => CaseClassParm[A,C], alias: String): JsonObjectCodecBuilder[A,B]
  def addIgnoredField(name: String): JsonObjectCodecBuilder[A,B]
  def addIgnoredFieldRegex(regex: Regex): JsonObjectCodecBuilder[A,B]
  def build: JsonTypedCodec[A,JsObj]
}
