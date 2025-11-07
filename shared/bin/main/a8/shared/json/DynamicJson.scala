package a8.shared.json

import a8.shared.json.DynamicJson.DynamicJsonOps
import a8.shared.json.ast._

import scala.language.dynamics
import a8.shared.SharedImports._
import a8.shared.json.JsonReader.JsonReaderOptions

object DynamicJson {

  val empty: DynamicJson = DynamicJson(JsNull)

  def fromIterable(iter: Iterable[DynamicJson]): DynamicJson =
    DynamicJson(JsArr(iter.map(_.__.asJsVal).toList))

  def apply(doc: JsVal) =
    new DynamicJson(doc, None)


  case class DynamicJsonOps(dynamicJson: DynamicJson, wrappedValue: JsVal, pathInfo: Option[(DynamicJson, Either[String,Int])]) {

    def merge(left: DynamicJson): DynamicJson = {
      val newJsVal =
        (left.__.asJsVal, wrappedValue) match {
          case (JsNothing | JsNull, r) =>
            r
          case (l, JsNothing | JsNull) =>
            l
          case (l@JsObj(_), r@JsObj(_)) =>
            JsObj(l.values ++ r.values)
          case (JsArr(l), JsArr(r)) =>
            JsArr(l ++ r)
          case (l, r) =>
            sys.error(s"don't know how to merge ${l} and ${r}")
        }
      DynamicJson(newJsVal)
    }

    def merge(right: JsObj): DynamicJson = {
      val newJsVal =
        wrappedValue match {
          case JsNothing | JsNull =>
            right
          case l@JsObj(_) =>
            JsObj(l.values ++ right.values)
          case l =>
            sys.error(s"don't know how to merge ${l} and ${right}")
        }
      DynamicJson(newJsVal)
    }

    def asBool: Boolean =
      wrappedValue match {
        case jsb: JsBool =>
          jsb.value
        case _ =>
          error(s"${wrappedValue} is not a boolean")
      }

    def asStringOpt: Option[String] = {
      wrappedValue match {
        case JsStr(s) =>
          Some(s)
        case _ =>
          None
      }
    }

    def asNumber: BigDecimal =
      asNumberOpt
        .getOrElse(error(s"unable to convert to number for -- ${asCompactStr}"))

    def asNumberOpt: Option[BigDecimal] =
      Some(wrappedValue)
        .flatMap {
          case JsNum(bd) =>
            Some(bd)
          case JsStr(s) =>
            try {
              Some(BigDecimal(s))
            } catch {
              case th: Throwable =>
                None
            }
          case _ =>
            None
        }

    /**
     *
     * @param cond
     * @return
     */
    def findOne(cond: DynamicJson=>Boolean): DynamicJson = {
      asArray
        .find(cond)
        .getOrElse(error("unable to find one"))
    }

    def asArray: Vector[DynamicJson] =
      asArray(false)

    def asArray(coerceJsObj: Boolean = false): Vector[DynamicJson] =
      (wrappedValue match {
        case JsArr(l) =>
          l.iterator
        case jo: JsObj if coerceJsObj =>
          Iterator(jo)
        case _ =>
          Iterator.empty
      })
        .zipWithIndex
        .map(v => new DynamicJson(v._1, Some((dynamicJson, Right(v._2)))))
        .toVector

    def asJsVal: JsVal =
      wrappedValue

    /**
     * TODO not ideal, ideally we turn this into a JsDocPath so we preserve the path info
     * @return
     */
    def asJsDoc: JsDoc = asJsVal.toRootDoc

    def asJsObj: JsObj =
      wrappedValue.asInstanceOf[JsObj]


    //  def asMoneyOpt: Option[String] =
    //    asNumberOpt.map(n => "$" + "%06.2f".format(n))
    //
    //  def asMoney: String =
    //    java.text.NumberFormat.getCurrencyInstance.format(asNumber)

    //  def asDateOnly: DateOnly =
    //    DynamicJson.uberDateParser.parseDateOnly(asString)
    //
    //  def asDateTime: DateTime =
    //    DynamicJson.uberDateParser.parseDateTime(asString)

    def asCompactStr = wrappedValue.compactJson

    def asPrettyStr = wrappedValue.prettyJson

    def asOption: Option[DynamicJson] =
      wrappedValue match {
        case JsNull | JsNothing =>
          None
        case _ =>
          Some(dynamicJson)
      }

    def path: String = {
      pathInfo match {
        case None =>
          ""
        case Some(t) =>
          val prefix = Some(t._1).filterNot(_.__.isRoot).map(_.__.path)
          t._2 match {
            case Left(n) =>
              prefix match {
                case Some(p) =>
                  p + "." + n
                case None =>
                  n
              }
            case Right(i) =>
              prefix match {
                case Some(p) =>
                  p + "[" + i + "]"
                case None =>
                  i.toString
              }
          }
      }
    }

    def isEmpty: Boolean =
      wrappedValue match {
        case JsArr(List()) | JsNull | JsNothing =>
          true
        case _ =>
          false
      }

    def isRoot = pathInfo.isEmpty

    def parent: Option[DynamicJson] = pathInfo.map(_._1)
    def parentOps: Option[DynamicJsonOps] = pathInfo.map(_._1.__)
    def root: DynamicJson = parentOps.map(_.root).getOrElse(dynamicJson)

    def modify(fn: JsVal => JsVal): DynamicJson =
      new DynamicJson(fn(wrappedValue), pathInfo)

    def withString(fn: String=>String): DynamicJson =
      dynamicJson
        .__
        .modify {
          case JsStr(s) =>
            JsStr(fn(s))
          case _ =>
            JsNothing
        }

    def withBoolean(fn: Boolean => JsVal): DynamicJson =
      dynamicJson.__.modify {
        case jsb: JsBool => fn(jsb.value)
        case _ => JsNothing
      }

    def withNumber(fn: BigDecimal => JsVal): DynamicJson =
      dynamicJson.__.modify {
        case JsNum(bd) => fn(bd)
        case _ => JsNothing
      }

    def error(msg: String): Nothing =
      sys.error(msg)

    def as[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): A =
      wrappedValue.unsafeAs[A]

  }

}




class DynamicJson(wrappedValue: JsVal, parent: Option[(DynamicJson, Either[String,Int])]) extends Dynamic { dynamicJson =>

  def selectDynamic(name: String): DynamicJson =
    selectDynamicOpt(name)
      .getOrElse {
        __.error(s"unable to find ${name} in -- ${__.asCompactStr}")
      }

  def selectDynamicOpt(name: String): Option[DynamicJson] =
    Some(wrappedValue)
      .collect { case jo: JsObj => jo }
      .flatMap { _.values.find(_._1 =:= name) }
      .map(t => new DynamicJson(t._2, Some(this -> Left(name))))

  def apply(name: String): DynamicJson = selectDynamic(name)
  def apply(index: Int): DynamicJson = __.asArray(index)

  override def toString: String =
    __.asPrettyStr

  def __ : DynamicJsonOps = DynamicJsonOps(this, wrappedValue, parent)

}
