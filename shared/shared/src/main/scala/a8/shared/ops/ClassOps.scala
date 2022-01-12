package a8.shared.ops


import a8.shared.SharedImports._

object ClassOps {
  def shortName(fullClassName: String): String =
    fullClassName
      .splitList("[\\.\\$]")
      .last
}

class ClassOps[A](private val clazz: Class[A]) extends AnyVal {

  def shortName = ClassOps.shortName(clazz.getName)

}