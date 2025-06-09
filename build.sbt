import sbtassembly.AssemblyPlugin.autoImport.assembly

import scala.collection.Seq

val V = new {
	val Scala = "3.3.4"
	val jakon = "0.7.0-SNAPSHOT"
}
val projectName = "template-app"
val projectVersion = "1.0.0"

scalaVersion := V.Scala
organization := "cz.kamenitxan"
name := projectName
version := projectVersion


ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / resolvers += "Artifactory" at "https://nexus.kamenitxan.eu/repository/jakon/"


val Dependencies = new {

	lazy val frontend = Seq(

	)

	lazy val backend = Seq(
		libraryDependencies ++=
			Seq(
				"cz.kamenitxan" %% "jakon" % V.jakon changing()
			)
	)

	lazy val tests = Def.settings(
		libraryDependencies ++= Seq(
			"org.scalatest" %% "scalatest" % "3.2.19" % Test,
			"org.seleniumhq.selenium" % "htmlunit3-driver" % "4.32.0" % Test
		)
	)
}

lazy val root = (project in file(".")).aggregate(frontend, backend)

lazy val frontend = (project in file("modules/frontend"))
	.enablePlugins(ScalaJSPlugin)
	.settings(scalaJSUseMainModuleInitializer := false)
	.settings(
		Dependencies.frontend,
		Dependencies.tests,
		Test / jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
	)
	.settings(
		commonBuildSettings,
		name := projectName + "-fe"
	)

lazy val backend = (project in file("modules/backend"))
	.settings(Dependencies.backend)
	.settings(Dependencies.tests)
	.settings(commonBuildSettings)
	.settings(
		name := projectName,
		Compile / mainClass := Some("cz.kamenitxan.templateapp.Main"),
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

lazy val commonBuildSettings: Seq[Def.Setting[_]] = Seq(
	scalaVersion := V.Scala,
	organization := "cz.kamenitxan",
	name := projectName,
	version := V.jakon,
	startYear := Some(2015)
)

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / semanticdbEnabled := false
ThisBuild / scalacOptions += "-deprecation"
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
Test / testForkedParallel := false
Test / parallelExecution:= false
Test / logBuffered := false

lazy val fastOptCompileCopy = taskKey[Unit]("")
val jsPath = "modules/backend/src/main/resources"
fastOptCompileCopy := {
	val source = (frontend / Compile / fastOptJS).value.data
	IO.copyFile(
		source,
		baseDirectory.value / jsPath / "dev.js"
	)
}

lazy val fullOptCompileCopy = taskKey[Unit]("")
fullOptCompileCopy := {
	val source = (frontend / Compile / fullOptJS).value.data
	IO.copyFile(
		source,
		baseDirectory.value / jsPath / "prod.js"
	)
}

addCommandAlias("runDev", ";fastOptCompileCopy; backend/reStart --mode dev")
addCommandAlias("runProd", ";fullOptCompileCopy; backend/reStart --mode prod")

val scalafixRules = Seq(
	"OrganizeImports",
	"DisableSyntax",
	"LeakingImplicitClassVal",
	"NoValInForComprehension"
).mkString(" ")

val CICommands = Seq(
	"clean",
	"backend/compile",
	"backend/test",
	"frontend/compile",
	"frontend/fastOptJS",
	"frontend/test",
	s"scalafix --check $scalafixRules"
).mkString(";")

val PrepareCICommands = Seq(
	s"scalafix $scalafixRules"
).mkString(";")

addCommandAlias("ci", CICommands)
addCommandAlias("preCI", PrepareCICommands)
addCommandAlias("outdated", "dependencyUpdates")