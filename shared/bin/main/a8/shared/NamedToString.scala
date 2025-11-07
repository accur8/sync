package a8.shared


import SharedImports._

trait NamedToString { self: Product =>


  override def toString: String = {
    val name = self.getClass.shortName
    val args =
      self
        .productElementNames
        .zip(self.productIterator)
        .map(t => t._1 + "=" + t._2)
        .mkString(", ")
    s"$name($args)"
  }


}
