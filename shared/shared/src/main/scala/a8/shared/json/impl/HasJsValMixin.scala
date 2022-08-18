package a8.shared.json.impl


import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, ReadError}
import zio.{Task, ZIO}

class HasJsValOps(private val self: HasJsVal) extends AnyVal {

  def toDoc: JsDoc = {
    self match {
      case jsd: JsDoc =>
        jsd
      case _ =>
        JsDoc(self.actualJsVal, None)
    }
  }

  def asObject: Option[JsObj] =
    self.actualJsVal match {
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

  def asF[A: JsonCodec]: Task[A] =
    ZIO.suspend {
      JsonCodec[A].read(toDoc) match {
        case Left(re) =>
          ZIO.fail(re.asException)
        case Right(v) =>
          ZIO.succeed(v)
      }
    }

  def compactPrintSortedKeys: String =
    JsValOps
      .toCompactJsonChord(self.actualJsVal, true)
      .toString

  def compactJson: String =
    JsValOps
      .toCompactJsonChord(self.actualJsVal, false)
      .toString

  def prettyJson: String =
    JsValOps
      .toPrettyJsonChord(self.actualJsVal)
      .toString

  def asStr: Option[String] =
    self.actualJsVal match {
      case JsStr(s) =>
        Some(s)
      case _ =>
        None
    }

  def asNum: Option[BigDecimal] =
    self.actualJsVal match {
      case JsNum(bd) =>
        Some(bd)
      case _ =>
        None
    }

  def asArr: List[JsVal] =
    self.actualJsVal match {
      case jsa: JsArr =>
        jsa.values
      case _ =>
        Nil
    }

  def apply(name: String): JsDoc = {
    val selectedValue =
      self.actualJsVal match {
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
      self.actualJsVal match {
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
