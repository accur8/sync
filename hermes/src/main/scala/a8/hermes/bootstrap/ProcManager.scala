package a8.hermes.bootstrap

/**
 * ProcManager answers one question about the CURRENT JVM process: "who is
 * supervising me, and under what name?" — detected BY the process, about ITSELF,
 * so a checkpoint auto-correlates with its systemd unit (or supervisord program)
 * with no enrollment and no outside-in observation.
 *
 * This is the Scala port of godev's `pkg/procmanager` (Go). Detection is a single
 * unprivileged read of /proc/self/cgroup (systemd) plus a look at supervisord's
 * env vars — no root, no systemd API. It MUST NEVER fail bootstrap: an unmanaged
 * process (bare CLI, container without systemd, dev laptop, macOS) gets the zero
 * value Managed.none with manager == "" and that is NORMAL, not an error.
 *
 * See FEATURE-20260714-self-detect-process-manager. The Go side of this same
 * detection lives in godev pkg/procmanager and the wire fields (processManager*)
 * are shared via continuum_rpc.proto.
 */
object ProcManager {

  // Manager identifiers (the Managed.manager value). Kept identical to the Go
  // pkg/procmanager constants so both producers emit the same wire values.
  val ManagerNone       = ""            // not managed / unknown — the common CLI case
  val ManagerSystemd    = "systemd"     // Linux systemd unit
  val ManagerWindowsSCM = "windows-scm" // Windows Service Control Manager (not detected here yet)
  val ManagerSupervisor = "supervisord" // supervisord program

  // Scope values for systemd (Managed.scope). Empty when not applicable.
  val ScopeSystem = "system"
  val ScopeUser   = "user"

  /**
   * What the current process detected about its own supervisor. The zero value
   * ([[none]], manager == ManagerNone) means "not managed / unknown" — a valid,
   * common answer, never an error.
   *
   * @param manager the process manager supervising this process, or ManagerNone.
   * @param unit    the manager-native identity: the systemd unit name
   *                ("a8-continuum-prod.service"), or the supervisord program
   *                ("group:program"). Empty when unmanaged.
   * @param scope   "system" | "user" for systemd; empty otherwise.
   */
  case class Managed(manager: String = ManagerNone, unit: String = "", scope: String = "") {

    /** Whether a supervising manager was detected. */
    def managed: Boolean = manager != ManagerNone

    /**
     * Render the detection as discovery metadata keys, for a process to publish
     * about itself. Returns an EMPTY map when unmanaged so a bare CLI adds nothing
     * — no "process_manager=" noise. Keys mirror the Go side:
     *
     *   process_manager       systemd | windows-scm | supervisord
     *   process_manager_unit  the unit/service/program name
     *   process_manager_scope system | user   (systemd only, when known)
     */
    def metadata: Map[String, String] =
      if (!managed) Map.empty
      else {
        var m = Map("process_manager" -> manager)
        if (unit.nonEmpty) m += ("process_manager_unit" -> unit)
        if (scope.nonEmpty) m += ("process_manager_scope" -> scope)
        m
      }
  }

  /** The unmanaged zero value. */
  val none: Managed = Managed()

  /**
   * Inspect the CURRENT process and return what supervises it. Never throws: an
   * undetectable/unmanaged process returns [[none]].
   *
   * Order matters: supervisord is checked FIRST because it is signalled by env
   * vars that work on every OS AND because a supervisord-managed process on Linux
   * still has a cgroup — but that cgroup is supervisord's own unit, NOT the managed
   * program. The env vars name the actual program, so they win. Only if not under
   * supervisord do we fall to the OS-specific detector (systemd cgroup on Linux,
   * nothing elsewhere).
   */
  def detect(): Managed =
    try {
      val sup = detectSupervisord()
      if (sup.managed) sup
      else detectOs()
    } catch {
      // Detection must never fail a bootstrap — swallow anything and report unmanaged.
      case _: Throwable => none
    }

  /**
   * Read the env vars supervisord injects into its children: SUPERVISOR_ENABLED=1
   * and SUPERVISOR_PROCESS_NAME=<program name>. Platform-neutral (env, no OS calls).
   * Returns [[none]] if not under supervisord.
   */
  private def detectSupervisord(): Managed = {
    if (sys.env.getOrElse("SUPERVISOR_ENABLED", "").isEmpty) none
    else {
      // The program name is the manager-native identity (like a systemd unit name).
      // SUPERVISOR_PROCESS_NAME is the base program; SUPERVISOR_GROUP_NAME wraps it
      // when the program is in a group (group:program). Prefer the qualified form
      // when they differ, so the id matches supervisorctl's own addressing.
      val procName = sys.env.getOrElse("SUPERVISOR_PROCESS_NAME", "")
      val grp = sys.env.getOrElse("SUPERVISOR_GROUP_NAME", "")
      val name = if (grp.nonEmpty && grp != procName) s"$grp:$procName" else procName
      Managed(manager = ManagerSupervisor, unit = name)
    }
  }

  /**
   * OS-specific detector. On Linux, resolve the systemd unit from
   * /proc/self/cgroup — one unprivileged read, no systemd API. Everywhere else
   * (macOS, Windows, container without systemd) there is nothing to detect and we
   * return [[none]]. Mirrors godev's build-tagged detect_linux.go / detect_other.go
   * with a runtime OS check (the JVM is one binary — no build tags).
   */
  private def detectOs(): Managed = {
    val osName = sys.props.getOrElse("os.name", "").toLowerCase
    if (!osName.contains("linux")) none
    else {
      val cgroupPath = java.nio.file.Paths.get("/proc/self/cgroup")
      if (!java.nio.file.Files.isReadable(cgroupPath)) none
      else {
        val content =
          try new String(java.nio.file.Files.readAllBytes(cgroupPath), java.nio.charset.StandardCharsets.UTF_8)
          catch { case _: Throwable => "" }
        val (unit, scope) = parseCgroupUnit(content)
        if (unit.isEmpty) none
        else Managed(manager = ManagerSystemd, unit = unit, scope = scope)
      }
    }
  }

  /**
   * Extract the systemd unit name + scope from /proc/self/cgroup content. Pure (no
   * I/O) so it is unit-testable across the cgroup formats:
   *
   *   cgroup v2:  "0::/system.slice/a8-continuum-prod.service"
   *   cgroup v1:  "1:name=systemd:/system.slice/foo.service" (one line among many)
   *   user scope: ".../user.slice/user-1000.slice/user@1000.service/.../foo.service"
   *
   * Returns ("", "") when no *.service (or *.scope) unit is present — e.g. a bare
   * process in the root cgroup, or a non-systemd container. Slices
   * (system.slice/user-1000.slice) are NOT units; only the trailing .service /
   * .scope leaf is the unit. Transient scopes (foo.scope) count as a unit too.
   */
  def parseCgroupUnit(content: String): (String, String) =
    content.split("\n").iterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { line =>
        // Each line is "hierarchy-ID:controllers:cgroup-path". Take the path (the
        // part after the SECOND colon; controllers may be empty for v2 "0::/...").
        var path = line
        val i = line.indexOf(':')
        if (i >= 0) {
          val rest = line.substring(i + 1)
          val j = rest.indexOf(':')
          if (j >= 0) path = rest.substring(j + 1)
        }
        unitFromPath(path)
      }
      .find { case (u, _) => u.nonEmpty }
      .getOrElse(("", ""))

  /**
   * Pull the systemd unit (the last path segment ending .service or .scope) from a
   * cgroup path, and infer scope from whether the path traverses a
   * user@<uid>.service / user.slice (user scope) vs system.slice (system scope).
   */
  private def unitFromPath(path: String): (String, String) = {
    val segs = path.split("/")
    // Scope: a path under user.slice / user-<uid>.slice / user@<uid>.service is a
    // user-scope unit; otherwise (system.slice) it is system scope. Default system.
    val scope =
      if (segs.exists(s => s == "user.slice" || s.startsWith("user-") || s.startsWith("user@"))) ScopeUser
      else ScopeSystem
    // Unit: the LAST segment that is a real unit (.service or .scope) and is NOT a
    // user@<uid>.service session-manager wrapper (that supervises the user session,
    // not this job — the real job unit is deeper).
    segs.reverseIterator
      .find(s => (s.endsWith(".service") || s.endsWith(".scope")) && !s.startsWith("user@"))
      .map(u => (u, scope))
      .getOrElse(("", ""))
  }
}
