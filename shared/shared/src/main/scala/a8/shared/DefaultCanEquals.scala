package a8.shared

import org.typelevel.ci.CIString

trait DefaultCanEquals {

  given CanEqual[CIString, CIString] = CanEqual.canEqualAny

}
