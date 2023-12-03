package a8.shared

import sttp.model.Uri
import sttp.model.Uri.Segment

class UriOps(val uri: Uri) extends AnyVal{

  def addPathZ(segments: ZString*): Uri =
    uri.addPath(segments.map(_.toString))

  def /(path: String): Uri =
    uri.addPath(Seq(path))

  def /(path: ZString): Uri =
    uri.addPath(Seq(path.toString))

  def /(path: Segment): Uri =
    uri.addPathSegment(path)

  def queryParms(name: String): Iterable[String] =
    uri.params.getMulti(name)

  def queryParm(name: String): Option[String] =
    uri.params.get(name)

}
