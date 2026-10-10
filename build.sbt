import sbtassembly.AssemblyPlugin.autoImport.assembly

val projectName = "template-app"
val projectVersion = "1.0.0"

ThisBuild / scalaVersion := V.Scala
ThisBuild / organization := "cz.kamenitxan"

ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / resolvers += "Artifactory" at "https://nexus.kamenitxan.eu/repository/jakon/"


lazy val root = (project in file("."))
	.aggregate(backend)
	.disablePlugins(AssemblyPlugin)
	.settings(
		name := projectName + "-root",
		version := projectVersion
	)

lazy val backend = (project in file("modules/backend"))
	.settings(Dependencies.backend)
	.settings(Dependencies.tests)
	.settings(commonBuildSettings)
	.settings(
		name := projectName,
		Compile / mainClass := Some("cz.kamenitxan.templateapp.Main"),
		Compile / run / fork := true,
		Test / fork := true,
		scalacOptions ++= Seq(
			"-deprecation", // emit warning and location for usages of deprecated APIs
			"-explain-types", // explain type errors in more detail
			"-feature", // emit warning and location for usages of features that should be imported explicitly
			"-no-indent", // do not allow significant indentation.
			"-print-lines", // show source code line numbers.
			"-unchecked", // enable additional warnings where generated code depends on assumptions
		)
	)

lazy val commonBuildSettings: Seq[Def.Setting[?]] = Seq(
	scalaVersion := V.Scala,
	organization := "cz.kamenitxan",
	name := projectName,
	version := projectVersion,
	startYear := Some(2015)
)

// semanticdb + unused imports warning are required by scalafix OrganizeImports
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalacOptions += "-Wunused:imports"
ThisBuild / assembly / assemblyMergeStrategy := {
	case PathList("module-info.class") => MergeStrategy.discard
	case x if x.endsWith("module-info.class") => MergeStrategy.discard
	case x if x.endsWith("BuildInfo$.class") => MergeStrategy.discard
	case x if x.endsWith("log4j2.xml") => MergeStrategy.first
	case x =>
		val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
		oldStrategy(x)
}

Test / fork := true
Test / parallelExecution:= false
Test / logBuffered := false

addCommandAlias("runDev", "backend/run --mode dev")
addCommandAlias("runProd", "backend/run --mode prod")

val scalafixRules = Seq(
	"OrganizeImports",
	"DisableSyntax",
	"LeakingImplicitClassVal",
	"NoValInForComprehension"
).mkString(" ")

val CICommands = Seq(
	"cleanFull",
	"backend/compile",
	"backend/testFull",
	s"backend/scalafix --check $scalafixRules"
).mkString(";")

val PrepareCICommands = Seq(
	s"backend/scalafix $scalafixRules"
).mkString(";")

addCommandAlias("ci", CICommands)
addCommandAlias("preCI", PrepareCICommands)
addCommandAlias("outdated", "dependencyUpdates")
