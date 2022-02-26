package a8.shared.json


import a8.shared.Chord
import a8.shared.Meta.CaseClassParm
import a8.shared.json.ast.{JsObj, JsStr, JsVal}

import scala.reflect.ClassTag
import a8.shared.SharedImports._

object UnionCodecBuilder {

  def apply[A]: UnionCodecBuilder[A] =
    UnionCodecBuilderImpl()

  case class UnionCodecBuilderType[A, B  <: A: ClassTag](name: String)(implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]) extends UnionType[A] {

    val classTag = scala.reflect.classTag[B]

    override def isInstanceOf(a: Any): Boolean =
      classTag.runtimeClass.isInstance(a)

    override def read(doc: ast.JsDoc): Either[ReadError, A] =
      jsonTypedCodec.read(doc)

    override def write(a: A): JsObj =
      jsonTypedCodec.write(a.asInstanceOf[B])

  }


  case class UnionCodecBuilderSingleton[A, B  <: A: ClassTag](name: String, singleton: B) extends UnionType[A] {

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
    val name: String
    lazy val nameJsStr = JsStr(name)
  }

  case class UnionCodecBuilderImpl[A](
    types: Vector[UnionType[A]] = Vector.empty,
    typeFieldName: String = "__type__"
  ) extends UnionCodecBuilder[A] {

    lazy val typesByName = types.map(t => t.name.toCi -> t).toMap

    override def typeFieldName(name: String): UnionCodecBuilder[A] =
      copy(typeFieldName = typeFieldName)

    override def addSingleton[B <: A : ClassTag](name: String, b: B): UnionCodecBuilder[A] =
      copy(types = types :+ UnionCodecBuilderSingleton[A,B](name, b))

    override def addType[B <: A : ClassTag](name: String)(implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]): UnionCodecBuilder[A] =
      copy(types = types :+ UnionCodecBuilderType[A,B](name))

    override def build: JsonTypedCodec[A,JsObj] =
      new JsonTypedCodec[A,JsObj] {

        override def write(a: A): ast.JsObj = {
          types.find(_.isInstanceOf(a)) match {
            case Some(ucbt) =>
              val untypedObj = ucbt.write(a)
              JsObj(untypedObj.values + (typeFieldName -> ucbt.nameJsStr))
            case None =>
              sys.error(s"don't know how to write ${a}")
          }
        }

        override def read(doc: ast.JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] = {
          doc(typeFieldName).value match {
            case JsStr(name) =>
              typesByName.get(name.toCi) match {
                case Some(t) =>
                  t.read(doc)
                case None =>
                  doc.errorL(s"no __type__ named ${name} found")
              }
            case _ =>
              doc.errorL("invalid __type__ field")
          }
        }

      }
  }

}

trait UnionCodecBuilder[A] {
  def typeFieldName(name: String): UnionCodecBuilder[A]
  def addSingleton[B <: A : ClassTag](name: String, b: B): UnionCodecBuilder[A]
  def addType[B <: A : ClassTag](name: String)(implicit jsonTypedCodec: JsonTypedCodec[B, JsObj]): UnionCodecBuilder[A]
  def build: JsonTypedCodec[A,JsObj]
}
