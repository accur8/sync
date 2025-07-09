package a8.common.logging

import sourcecode.FullName

object Trace {
//  implicit def apply(): Trace = empty
  inline given trace(using file: sourcecode.File, line: sourcecode.Line, fullName: FullName): Trace =
    Trace(file, line, fullName)
}

case class Trace(file: sourcecode.File, line: sourcecode.Line, fullName: FullName) {
  def loggerName: String = fullName.value
}

