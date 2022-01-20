
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

// Profile name of sonatype account
ThisBuild / sonatypeProfileName := "io.accur8"

// Needed to sync with Maven central
ThisBuild / publishMavenStyle := true

// Open-source license
ThisBuild / licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

// Source code location
ThisBuild / homepage := Some(url("https://github.com/accur8/sync"))
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/accur8/sync"), "scm:git@github.com:accur8/sync.git"))
ThisBuild / developers := List(
  Developer(id="fizzy33", name="Glen Marchesani", email="glen@accur8software.com", url=url("https://github.com/fizzy33")),
  Developer(id="renns", name="Raphael Enns", email="raphael@accur8software.com", url=url("https://github.com/renns")),
)
