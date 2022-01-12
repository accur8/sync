package a8.shared

import org.scalajs.dom

trait JsRuntimePlatformImpl {
  self: RuntimePlatform =>

  object impl {
    def userAgentMatch(ua: String): Boolean =
      dom.window.navigator.userAgent.contains(ua)
  }

  override val isWindows: Boolean = impl.userAgentMatch("Windows NT")
  override val isMac: Boolean = impl.userAgentMatch("Mac OS X")

  override val isJvm: Boolean = false
  override val isJs: Boolean = true

}
