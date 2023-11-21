
// 
// DO NOT EDIT THIS FILE IT IS MACHINE GENERATED
// 
// This file is generated from modules.conf using `a8-versions build_dot_sbt`
// 
// It was generated at 2022-01-31T17:26:05.981685900 by glen on fullfillment
// 
// a8-versions build/versioning info follows
// 
// 
// 
//      

val appVersion = a8.sbt_a8.versionStamp(file("."))

val scalaLibVersion = "3.3.1"
val zioVersion = "2.0.19"
val zioLoggingVersion = "2.1.15"
val slf4jVersion = "2.0.9"
val zeroWasteVersion = "0.2.15"

val zeroWastePlugin = compilerPlugin("com.github.ghik" % "zerowaste" % zeroWasteVersion cross CrossVersion.full)


Global / resolvers += {
  try {
    "a8-repo" at Common.readRepoUrl()
  } catch {
    case e: RuntimeException =>
      val log = sLog.value
      log.warn(s"WARNING: a8-repo not found, using default maven repo -- ${e.getMessage}")
      "Oracle Repository" at "https://download.oracle.com/maven"
  }
}

Global / publishTo := {
  try {
    Some("a8-repo-releases" at Common.readRepoUrl())
  } catch {
    case e: RuntimeException =>
      val log = sLog.value
      log.warn(s"WARNING: no publishTo configured -- ${e.getMessage}")
      None
  }
}

Global / credentials ++= {
  try {
    Some(Common.readRepoCredentials())
  } catch {
    case e: RuntimeException =>
      val log = sLog.value
      log.warn(s"WARNING: no credentials configured -- ${e.getMessage}")
      None
  }
}

//Global / publishTo := sonatypePublishToBundle.value
//Global / credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")

Global / scalaVersion := scalaLibVersion

Global / organization := "io.accur8"

Global / version := appVersion

Global / versionScheme := Some("strict")

Global / serverConnectionType := ConnectionType.Local

//Global / scalacOptions ++= Seq(
//  "-deprecation",
//  "-feature",
//  "-unchecked",
//  "-language:higherKinds",
//  "-language:implicitConversions",
//  "-language:strictEquality",
//  "-Xfatal-warnings",
//  "-Xlint",
//  "-Yinline-warnings",
//  "-Yno-adapted-args",
//  "-Ywarn-dead-code",
//  "-Ywarn-numeric-widen",
//  "-Ywarn-value-discard",
//  "-Xfuture",
////  "-Ywarn-unused-import"
//)
Global / scalacOptions ++= Seq(
  // "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-language:strictEquality",
 // "-Werror",
)

lazy val logging =
  Common
    .crossProject("a8-logging", file("logging"), "logging")
    .settings(
      libraryDependencies ++= Seq(
        "com.lihaoyi" %%% "sourcecode" % "0.3.0",
        "dev.zio" %%% "zio" % zioVersion,
        "dev.zio" %% "zio-logging" % zioLoggingVersion,
        zeroWastePlugin,
      )
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-api" % slf4jVersion,
        "dev.zio" %% "zio-logging-slf4j2" % zioLoggingVersion,
      )
    )
    .jsSettings(
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "2.6.0",
      )
    )

lazy val loggingJVM = logging.jvm
lazy val loggingJS = logging.js

lazy val logging_logback =
  Common
    .jvmProject("a8-logging-logback", file("logging_logback"), "logging_logback")
    .dependsOn(loggingJVM)
    .settings(
      libraryDependencies ++= Seq(
//        zeroWastePlugin,
      )
    )
    .settings(
      libraryDependencies ++= Seq(
        "org.codehaus.janino" % "janino" % "3.1.8",
        "org.fusesource.jansi" % "jansi" % "2.4.0",
        "ch.qos.logback" % "logback-core" % "1.4.11",
        "ch.qos.logback" % "logback-classic" % "1.4.11",
        "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
        "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
      )
    )

lazy val logging_logback_test =
  Common
    .jvmProject("a8-logging-logback_test", file("logging_logback_test"), "logging_logback_test")
    .dependsOn(logging_logback)

lazy val api =
  Common
    .jvmProject("a8-sync-api", file("api"), "api")
    .dependsOn(shared)
    .settings(
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.2.15" % "test",
        zeroWastePlugin,
      )
    )

lazy val http =
  Common
    .jvmProject("a8-http-server", file("http-server"), "http-server")
    .dependsOn(api)
    .settings(
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-http" % "3.0.0-RC1",
        "org.scalatest" %% "scalatest" % "3.2.15" % "test",
        zeroWastePlugin,
      )
    )


lazy val shared =
  Common
    .jvmProject("a8-sync-shared", file("shared"), "shared")
    .dependsOn(logging_logback)
//    .crossProject("a8-sync-shared", file("shared"), "shared")
    .settings(
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      Compile / unmanagedSourceDirectories :=
        Seq(
          baseDirectory.value / "jvm" / "src" / "main" / "scala",
          baseDirectory.value / "shared" / "src" / "main" / "scala",
        ),
      Compile / unmanagedResourceDirectories :=
        Seq(
          baseDirectory.value / "jvm" / "src" / "main" / "resources",
          baseDirectory.value / "shared" / "src" / "main" / "resources",
        ),
      Test / unmanagedResourceDirectories :=
        Seq(
          baseDirectory.value / "jvm" / "src" / "test" / "resources",
          baseDirectory.value / "shared" / "src" / "test" / "resources",
        ),
      Test / unmanagedSourceDirectories :=
        Seq(
          baseDirectory.value / "jvm" / "src" / "test" / "scala",
          baseDirectory.value / "shared" / "src" / "test" / "scala",
        ),
      libraryDependencies ++= Seq(
        zeroWastePlugin,
        "org.typelevel" %% "case-insensitive" % "1.3.0",
        "com.beachape" %%% "enumeratum" % "1.7.2",
        "com.lihaoyi" %%% "sourcecode" % "0.3.0",
        "com.softwaremill.sttp.model" %% "core" % "1.5.5",
        "org.scalactic" %% "scalactic" % "3.2.15" % "test",
        "org.scalatest" %% "scalatest" % "3.2.15" % "test",
        "org.typelevel" %% "jawn-parser" % "1.4.0",
        "org.typelevel" %% "jawn-ast" % "1.4.0",
        "dev.zio" %%% "zio-prelude" % "1.0.0-RC16",
        "dev.zio" %%% "zio-streams" % zioVersion,
        "dev.zio" %%% "zio-test" % zioVersion % Test,
        "dev.zio" %%% "zio-test-sbt" % zioVersion % Test,
        "dev.zio" %%% "zio-test-magnolia" % zioVersion % Test,
//      )
//    )
//    .jvmSettings(
//      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.client3" %% "zio" % "3.8.11",
        "org.hsqldb" % "hsqldb" % "2.7.1",
        "dev.zio" %% "zio-cache" % "0.2.2",
        "com.github.andyglow" %% "typesafe-config-scala" % "2.0.0",
        "org.postgresql" % "postgresql" % "42.5.3",
        "mysql" % "mysql-connector-java" % "8.0.32",
        "net.sf.jt400" % "jt400" % "11.1",
        "com.zaxxer" % "HikariCP" % "5.0.1",
        "com.sun.mail" % "jakarta.mail" % "2.0.1",
      )
    )
//    .jsSettings(
//      libraryDependencies ++= Seq(
//        "org.scala-js" %%% "scalajs-dom" % "2.2.0",
//      )
//    )

//lazy val sharedJVM = shared.jvm
//lazy val sharedJS = shared.js


lazy val root =
  Common.jvmProject("root", file("target/root"), id = "root")
    .settings( publish := {} )
    .settings( com.jsuereth.sbtpgp.PgpKeys.publishSigned := {} )
    .aggregate(api)
    .aggregate(http)
    .aggregate(shared)
    .aggregate(loggingJVM)
    .aggregate(loggingJS)
    .aggregate(logging_logback)
    .aggregate(logging_logback_test)



   
