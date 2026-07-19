package a8.hermes.bootstrap

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for ProcManager — the Scala port of godev's pkg/procmanager. Mirrors
 * that package's tests: metadata rendering, the managed predicate, and cgroup
 * parsing across v1/v2/user-scope formats. The supervisord env-var path can't be
 * driven without mutating process env, so it's covered by asserting the shape of
 * Managed directly and by the pure parse helpers.
 */
class ProcManagerTest extends AnyFunSuite with Matchers {
  import ProcManager._

  test("unmanaged -> empty metadata (no process_manager= noise for a bare CLI)") {
    none.metadata shouldBe empty
    Managed().managed shouldBe false
  }

  test("systemd system scope -> all three metadata keys") {
    val md = Managed(ManagerSystemd, "a8-continuum-prod.service", ScopeSystem).metadata
    md("process_manager") shouldBe "systemd"
    md("process_manager_unit") shouldBe "a8-continuum-prod.service"
    md("process_manager_scope") shouldBe "system"
  }

  test("manager set but no scope -> no scope key") {
    val md = Managed(ManagerSupervisor, "myprog").metadata
    md.get("process_manager_scope") shouldBe None
    md("process_manager_unit") shouldBe "myprog"
  }

  test("managed predicate") {
    Managed().managed shouldBe false
    Managed(ManagerSystemd).managed shouldBe true
  }

  test("cgroup v2: system.slice service") {
    val (u, sc) = parseCgroupUnit("0::/system.slice/a8-continuum-prod.service\n")
    u shouldBe "a8-continuum-prod.service"
    sc shouldBe ScopeSystem
  }

  test("cgroup v1: name=systemd line among many") {
    val content =
      """12:pids:/system.slice/foo.service
        |11:memory:/system.slice/foo.service
        |1:name=systemd:/system.slice/foo.service
        |0::/system.slice/foo.service""".stripMargin
    val (u, sc) = parseCgroupUnit(content)
    u shouldBe "foo.service"
    sc shouldBe ScopeSystem
  }

  test("user scope: user@uid.service wrapper is skipped, inner unit + user scope wins") {
    val (u, sc) = parseCgroupUnit(
      "0::/user.slice/user-1000.slice/user@1000.service/app.slice/myjob.service\n")
    u shouldBe "myjob.service"
    sc shouldBe ScopeUser
  }

  test("transient .scope unit counts") {
    val (u, sc) = parseCgroupUnit("0::/system.slice/a8-jobrun-abc.scope\n")
    u shouldBe "a8-jobrun-abc.scope"
    sc shouldBe ScopeSystem
  }

  test("bare process in root cgroup -> no unit") {
    val (u, _) = parseCgroupUnit("0::/\n")
    u shouldBe ""
  }

  test("bare user@uid.service with no inner unit -> no job unit") {
    // Just the user session manager, not a job — no *.service leaf beyond the wrapper.
    val (u, _) = parseCgroupUnit("0::/user.slice/user-1000.slice/user@1000.service\n")
    u shouldBe ""
  }

  test("detect never throws and returns a valid Managed") {
    val m = ProcManager.detect()
    // On a dev laptop / CI this is typically unmanaged; on a systemd host it's systemd.
    // Either way it must be a well-formed value with consistent metadata.
    if (m.managed) m.metadata("process_manager") shouldBe m.manager
    else m.metadata shouldBe empty
  }
}
