package a8.hermes.bootstrap

import a8.hermes.proto.discovery.discovery.BuildInfo

/**
 * Reads this JVM's build identity from the baked-in
 * META-INF/version-details.properties resource and renders it as the structured
 * discovery.BuildInfo — the Scala half of FEATURE-20260709-processstart-build-info,
 * so a checkpoint's processrun answers "which binary is this?" the same way a Go
 * process does (godev pkg/buildinfo.ToProto). The resource is stamped at build
 * time by the sbt version-details task; git_commit is parsed out of the embedded
 * `# git log` comment block since it is not a top-level key.
 *
 * Read ONCE at object init (the resource is immutable for the process lifetime).
 * Every failure mode — resource absent (a raw `sbt run` before a stamped build),
 * unreadable, malformed — degrades to BuildInfo.defaultInstance (all fields ""),
 * never an exception: missing build info must not fail a bootstrap, exactly as the
 * Go side treats "unknown".
 */
object BuildInfoReader {

  /** The structured build identity, resolved once. Empty fields when unstamped. */
  val buildInfo: BuildInfo = read()

  private def read(): BuildInfo =
    try {
      val in = getClass.getResourceAsStream("/META-INF/version-details.properties")
      if (in == null) BuildInfo.defaultInstance
      else {
        val text =
          try new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
          finally in.close()
        fromProperties(text)
      }
    } catch {
      case _: Throwable => BuildInfo.defaultInstance
    }

  /**
   * Parse the version-details text into a BuildInfo. Pure (no I/O) so the field
   * mapping is unit-testable. The top of the file is `key=value` lines; the git
   * commit sha lives only inside the `#  git log` comment block as the first
   * `#  commit <sha>` line, so it is extracted separately.
   */
  def fromProperties(text: String): BuildInfo = {
    val props = scala.collection.mutable.Map.empty[String, String]
    for (raw <- text.linesIterator) {
      val line = raw.trim
      // key=value lines only; comment (#) and blank lines are skipped, so the git-log
      // body (all `#`-prefixed) never pollutes the property map.
      if (line.nonEmpty && !line.startsWith("#")) {
        val eq = line.indexOf('=')
        if (eq > 0) props(line.substring(0, eq).trim) = line.substring(eq + 1).trim
      }
    }
    BuildInfo(
      version        = props.getOrElse("version_number", ""),
      gitRepo        = props.getOrElse("project_name", ""),
      gitCommit      = firstCommitSha(text),
      // git_branch / git_dirty are not stamped as keys on the Scala side (they live
      // only as free-text in the git-status/git-branch comment blocks); leave empty.
      buildTimestamp = props.getOrElse("build_date", ""),
      buildMachine   = props.getOrElse("build_machine", ""),
      buildUser      = props.getOrElse("build_user", ""),
      buildIps       = props.getOrElse("build_machine_ip", ""),
      // The JVM/Scala runtime version is this language's analog of go_version.
      goVersion      = props.getOrElse("build_java_version", ""),
      goos           = props.getOrElse("build_os", ""),
      // goarch is not stamped in version-details; leave empty.
    )
  }

  /**
   * Pull the build's git commit sha out of the `# git log` comment block: the first
   * line matching `#  commit <40-hex>`. Returns "" when absent.
   */
  private def firstCommitSha(text: String): String = {
    val prefix = "commit "
    text.linesIterator
      .map(_.trim.stripPrefix("#").trim) // "#  commit abc..." -> "commit abc..."
      .collectFirst {
        case l if l.startsWith(prefix) && isHex(l.substring(prefix.length).trim) =>
          l.substring(prefix.length).trim
      }
      .getOrElse("")
  }

  private def isHex(s: String): Boolean =
    s.length >= 7 && s.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
}
