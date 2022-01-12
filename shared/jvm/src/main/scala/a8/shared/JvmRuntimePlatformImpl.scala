package a8.shared

trait JvmRuntimePlatformImpl {
  self: RuntimePlatform =>

  override val isWindows: Boolean = System.getProperty("os.name", "").contains("Windows")
  override val isMac: Boolean = System.getProperty("os.name") == "Mac OS X"
  override val isJvm: Boolean = true
  override val isJs: Boolean = false

}
