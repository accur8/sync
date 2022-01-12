package a8.shared

object RuntimePlatform extends RuntimePlatform with RuntimePlatformImpl {


}



trait RuntimePlatform {

  val isWindows: Boolean
  val isMac: Boolean
  val isJvm: Boolean
  val isJs: Boolean

}

