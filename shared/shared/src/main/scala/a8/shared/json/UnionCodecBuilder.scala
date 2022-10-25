package a8.shared.json


import a8.shared.Chord
import a8.shared.Meta.CaseClassParm
import a8.shared.json.ast.{JsNothing, JsNull, JsObj, JsStr, JsVal}

import scala.reflect.ClassTag
import a8.shared.SharedImports._

object UnionCodecBuilder {

  def apply[A]: UnionCodecBuilder[A] =
    UnionCodecBuilderImpl()

  case class UnionCodecBuilderType[A, B  <: A: ClassTag](name: Option[String])(implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]) extends UnionType[A] {

    val classTag = scala.reflect.classTag[B]

    override def isInstanceOf(a: Any): Boolean =
      classTag.runtimeClass.isInstance(a)

    override def read(doc: ast.JsDoc): Either[ReadError, A] =
      jsonTypedCodec.read(doc)

    override def write(a: A): JsObj =
      jsonTypedCodec.write(a.asInstanceOf[B])

  }


  case class UnionCodecBuilderSingleton[A, B  <: A: ClassTag](name: Option[String], singleton: B) extends UnionType[A] {

    override def isInstanceOf(a: Any): Boolean =
      a == singleton

    override def read(doc: ast.JsDoc): Either[ReadError, A] =
      Right(singleton)

    override def write(a: A): JsObj =
      JsObj.empty

  }

  sealed trait UnionType[A] {
    def read(doc: ast.JsDoc): Either[ReadError, A]
    def write(a: A): JsObj
    def isInstanceOf(a: Any): Boolean
    val name: Option[String]
    lazy val nameJsStr = name.map(JsStr.apply)
  }

  case class UnionCodecBuilderImpl[A](
    types: Vector[UnionType[A]] = Vector.empty,
    typeFieldName: String = "__type__",
  ) extends UnionCodecBuilder[A] {

    lazy val defaultTypeOpt = typesByName.get(None)

    lazy val typesByName = types.map(t => t.name.map(_.toCi) -> t).toMap

    override def defaultType[B <: A : ClassTag](implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]): UnionCodecBuilder[A] =
      copy(types = types :+ UnionCodecBuilderType[A,B](None))

    override def typeFieldName(name: String): UnionCodecBuilder[A] =
      copy(typeFieldName = name)

    override def addSingleton[B <: A : ClassTag](name: String, b: B): UnionCodecBuilder[A] =
      copy(types = types :+ UnionCodecBuilderSingleton[A,B](name.some, b))

    override def addType[B <: A : ClassTag](name: String)(implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]): UnionCodecBuilder[A] =
      copy(types = types :+ UnionCodecBuilderType[A,B](name.some))

    override def build: JsonTypedCodec[A,JsObj] =
      new JsonTypedCodec[A,JsObj] {

        override def write(a: A): ast.JsObj = {
          types.find(_.isInstanceOf(a)) match {
            case Some(ucbt) =>
              val untypedObj = ucbt.write(a)
              JsObj(untypedObj.values ++ ucbt.nameJsStr.map(typeFieldName -> _))
            case None =>
              sys.error(s"don't know how to write ${a}")
          }
        }

        override def read(doc: ast.JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] = {
          doc(typeFieldName).value match {
            case JsNull | JsNothing if defaultTypeOpt.nonEmpty =>
              defaultTypeOpt.get.read(doc)
            case JsStr(name) =>
              typesByName.get(name.toCi.some) match {
                case Some(t) =>
                  t.read(doc.removeField(typeFieldName))
                case None =>
                  doc.errorL(s"no ${typeFieldName} named ${name} found")
              }
            case _ =>
              doc.errorL(s"invalid ${typeFieldName} field")
          }
        }

      }
  }

}

trait UnionCodecBuilder[A] {
  def typeFieldName(name: String): UnionCodecBuilder[A]
  def addSingleton[B <: A : ClassTag](name: String, b: B): UnionCodecBuilder[A]
  def addType[B <: A : ClassTag](name: String)(implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]): UnionCodecBuilder[A]
  def defaultType[B <: A : ClassTag](implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]): UnionCodecBuilder[A]
  def build: JsonTypedCodec[A,JsObj]
}
