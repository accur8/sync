package a8.shared.json.impl


import a8.shared.json.JsonReader.{JsonReaderOptions, ReadResult}
import a8.shared.json.ast.JsDoc.{JsDocPath, JsDocRoot}
import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, JsonReader, ReadError}
import a8.shared.SharedImports._

object HasJsValOps {
}

class HasJsValOps(private val self: HasJsVal) extends AnyVal {

  def toRootDoc: JsDocRoot = JsDocRoot(self.actualJsVal)

  private def toJsObj(prefix: String, hasjsv: HasJsVal): JsObj =
    hasjsv.actualJsVal match {
      case jo: JsObj =>
        jo
      case ja: JsArr =>
        ja.asJsObj
      case JsNothing | JsNull =>
        JsObj.empty
      case v =>
        JsObj(Map(prefix -> v))
    }

  private def strictToJsObj(hasjsv: HasJsVal): Option[JsObj] =
    hasjsv.actualJsVal match {
      case jo: JsObj =>
        jo.some
      case ja: JsArr =>
        ja.asJsObj.some
      case JsNothing | JsNull =>
        JsObj.empty.some
      case _ =>
        None
    }

  private def toDoc: JsDoc = {
    self match {
      case jsd: JsDoc =>
        jsd
      case _ =>
        JsDocRoot(self.actualJsVal)
    }
  }

  def strictMerge(right: HasJsVal): Option[JsVal] = {
    (strictToJsObj(self), strictToJsObj(right)) match {
      case (Some(l), Some(r)) =>
        Some(l.merge(r))
      case _ =>
        None
    }
  }

  def merge(right: HasJsVal): JsVal = {
    toJsObj("fromLeftMerge", self)
      .merge(toJsObj("fromRightMerge", right))
  }

  def asObject: Option[JsObj] =
    self.actualJsVal match {
      case jo: JsObj =>
        Some(jo)
      case _ =>
        None
    }

  def unsafeAs[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): A =
    as[A] match {
      case Left(re) =>
        throw re.asException
      case Right(a) =>
        a
    }

  def as[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): Either[ReadError,A] =
    JsonReader[A].readResult(self.actualJsVal) match {
      case ReadResult.Success(a, _, _, _) =>
        Right(a)
      case ReadResult.Error(re, _, _) =>
        Left(re)
    }

//  !!! ???
//  def asF[A: JsonCodec](implicit jsonReaderZOptions: JsonReaderOptions): Task[A] =
//    !!!
//    ZJsonReader[A]
//      .read(self.actualJsVal)

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
    JsDocPath(selectedValue, toDoc, Left(name))
  }

  def apply(index: Int): JsDoc = {
    val selectedValue =
      self.actualJsVal match {
        case jarr: JsArr =>
          if ( index >= 0 && index < jarr.values.size )
            jarr.values(index)
          else
            JsNothing
        case _ =>
          JsNothing
      }
    JsDocPath(selectedValue, toDoc, Right(index))
  }

}
