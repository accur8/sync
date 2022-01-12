
// 
// DO NOT EDIT THIS FILE IT IS MACHINE GENERATED
// 
// This file is generated from modules.conf using `a8-versions build_dot_sbt`
// 
// It was generated at 2022-01-12 11:25:34.837 -0600 by raph on ENNS-PC
// 
// a8-versions build/versioning info follows
// 
//        build_date : Thu Sep 30 12:56:07 CDT 2021
//        build_machine : ENNS-PC
//        build_machine_ip : 127.0.1.1
//        build_java_version : 11.0.11
//        build_user : raph
//        version_number : 1.0.0-20210930_1255_master
//        project_name : a8-versions
//        build_os : Linux
// 
//      

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.9")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.6.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
//addSbtPlugin("io.get-coursier" % "sbt-coursier" % "2.0.0-RC6")
//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")

resolvers += "a8-sbt-plugins" at readRepoUrl()
credentials += readRepoCredentials()

//libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.21"
//addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")

// use this one if you need dobby
//addSbtPlugin("a8" % "sbt-a8" % "1.1.0-20210702_1452")
addSbtPlugin("a8" % "sbt-a8" % "1.1.0-20210930_1248")

// This plugin can be removed when using Scala 2.13.0 or above
addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")




  def readRepoUrl() = readRepoProperty("repo_url")

  lazy val repoConfigFile = new java.io.File(System.getProperty("user.home") + "/.a8/repo.properties")

  lazy val repoProperties = {
    import scala.jdk.CollectionConverters._
    val props = new java.util.Properties()
    if ( repoConfigFile.exists() ) {
      val input = new java.io.FileInputStream(repoConfigFile)
      try {
        props.load(input)
      } finally {
        input.close()
      }
      props.asScala
    } else {
      sys.error("config file " + repoConfigFile + " does not exist")
    }
  }

  def readRepoProperty(propertyName: String): String = {
    repoProperties.get(propertyName) match {
      case Some(s) =>
        s
      case None =>
        sys.error("could not find property " + propertyName + " in " + repoConfigFile)
    }
  }

  def readRepoCredentials(): Credentials = {
    val repoUrl = new java.net.URL(readRepoUrl())
    Credentials(
      readRepoProperty("repo_realm"),
      repoUrl.getHost,
      readRepoProperty("repo_user"),
      readRepoProperty("repo_password"),
    )
  }


  

