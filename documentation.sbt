// Documentation command aliases for Accur8 Sync

// Basic commands that should always work
addCommandAlias("compileAll", ";compile;test:compile")
addCommandAlias("testAll", ";test")

// Documentation generation - requires manual plugin setup
// To use these commands, first enable the plugins on the root project:
// sbt> project root
// sbt> enablePlugins(ScalaUnidocPlugin)
// sbt> enablePlugins(MdocPlugin)
// sbt> enablePlugins(SiteScaladocPlugin)
// sbt> enablePlugins(GhpagesPlugin)

addCommandAlias("generateApiDocs", ";project root;clean;compile;doc")
addCommandAlias("checkPlugins", ";project root;plugins")