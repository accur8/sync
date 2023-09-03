package a8.common

package object logging {

  /**
    * Take the scala'cized classname and make it more human readable.
    * So xyz.pdq.Main2$foo$bar$ becomes xyz.pdq.Main2.foo.bar
    */
  def normalizeClassname(classname: String): String =
    classname.replace("$", ".").reverse.dropWhile(_ == '.').reverse

  def normalizeClassname(clazz: Class[_]): String =
    normalizeClassname(clazz.getName)

}
