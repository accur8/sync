package a8.hermes.bootstrap

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for BuildInfoReader — the Scala half of
 * FEATURE-20260709-processstart-build-info. Drives the pure fromProperties parser
 * against the real version-details.properties shape (key=value header + a git-log
 * comment block that carries the commit sha).
 */
class BuildInfoReaderTest extends AnyFunSuite with Matchers {

  private val sample =
    """
      |project_name=a8-hermes
      |version_number=1.0.0-20260719_0701_master
      |build_date=Sun Jul 19 07:02:03 EDT 2026
      |build_machine=starbak
      |build_user=glen
      |build_machine_ip=192.168.0.234
      |build_os=Mac OS X
      |build_java_version=21.0.8
      |
      |
      |#  git log -n 99
      |#
      |#  commit 249b0af4e97f9ac2abcf0684d53b61c87f0e8f3b
      |#  Author: Glen Marchesani <glen@accur8software.com>
      |#  Date:   Sun Jul 19 06:01:35 2026 -0400
      |#
      |#      hermes: self-detect process manager and publish it at bootstrap
      |""".stripMargin

  test("maps header keys to BuildInfo fields") {
    val bi = BuildInfoReader.fromProperties(sample)
    bi.version shouldBe "1.0.0-20260719_0701_master"
    bi.gitRepo shouldBe "a8-hermes"
    bi.buildTimestamp shouldBe "Sun Jul 19 07:02:03 EDT 2026"
    bi.buildMachine shouldBe "starbak"
    bi.buildUser shouldBe "glen"
    bi.buildIps shouldBe "192.168.0.234"
    bi.goVersion shouldBe "21.0.8" // JVM version is this language's runtime version
    bi.goos shouldBe "Mac OS X"
  }

  test("extracts the git commit sha from the git-log comment block") {
    BuildInfoReader.fromProperties(sample).gitCommit shouldBe "249b0af4e97f9ac2abcf0684d53b61c87f0e8f3b"
  }

  test("git-log comment body does not leak into fields (only header key=value lines parsed)") {
    val bi = BuildInfoReader.fromProperties(sample)
    // The comment block has 'Author:'/'Date:' lines but those are # prefixed, so no
    // stray key should have captured them.
    bi.gitBranch shouldBe "" // not stamped as a key
    bi.goarch shouldBe ""    // not stamped
  }

  test("empty / unstamped input degrades to all-empty, never throws") {
    val bi = BuildInfoReader.fromProperties("")
    bi.version shouldBe ""
    bi.gitCommit shouldBe ""
    bi shouldBe a8.hermes.proto.discovery.discovery.BuildInfo.defaultInstance
  }

  test("no commit line -> empty gitCommit (not a crash)") {
    BuildInfoReader.fromProperties("version_number=9\n").gitCommit shouldBe ""
  }
}
