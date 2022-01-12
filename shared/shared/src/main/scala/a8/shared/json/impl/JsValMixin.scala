package a8.shared.json.impl

import a8.shared.SharedImports.Async
import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, ReadError}

trait JsValMixin { self: JsVal =>

  def actualValue: JsVal =
    self match {
      case jsd: JsDoc =>
        jsd.value
      case _ =>
        self
    }

  def toDoc: JsDoc =
    self match {
      case jsd: JsDoc =>
        jsd
      case jv =>
        JsDoc(jv, None)
    }

  def asObject: Option[JsObj] =
    actualValue match {
      case jo: JsObj =>
        Some(jo)
      case _ =>
        None
    }

  def unsafeAs[A : JsonCodec]: A =
    as[A] match {
      case Left(re) =>
        throw re.asException
      case Right(a) =>
        a
    }

  def as[A : JsonCodec]: Either[ReadError,A] =
    JsonCodec[A].read(toDoc)

  def asF[F[_] : Async, A : JsonCodec]: F[A] =
    Async[F].defer {
      JsonCodec[A].read(toDoc) match {
        case Left(re) =>
          Async[F].raiseError(re.asException)
        case Right(v) =>
          Async[F].pure(v)
      }
    }

  def compactPrintSortedKeys: String =
    JsValOps
      .toCompactJsonChord(self, false)
      .toString

  def compactJson: String =
    JsValOps
      .toCompactJsonChord(self, false)
      .toString

  def prettyJson: String =
    JsValOps
      .toPrettyJsonChord(self)
      .toString

  def asStr: Option[String] =
    actualValue match {
      case JsStr(s) =>
        Some(s)
      case _ =>
        None
    }

  def asNum: Option[BigDecimal] =
    actualValue match {
      case JsNum(bd) =>
        Some(bd)
      case _ =>
        None
    }

  def asArr: List[JsVal] =
    actualValue match {
      case jsa: JsArr =>
        jsa.values
      case _ =>
        Nil
    }

  def apply(name: String): JsDoc = {
    val selectedValue =
      self.toDoc.value match {
        case jobj: JsObj =>
          jobj
            .values
            .get(name)
            .getOrElse(JsNothing)
        case _ =>
          JsNothing
      }
    JsDoc(selectedValue, Some(toDoc -> Left(name)))
  }

  def apply(index: Int): JsDoc = {
    val selectedValue =
      self.toDoc.value match {
        case jarr: JsArr =>
          if ( index >= 0 && index <= jarr.values.size )
            jarr.values(index)
          else
            JsNothing
        case _ =>
          JsNothing
      }
    JsDoc(selectedValue, Some(toDoc -> Right(index)))
  }

}
