import sbtassembly.AssemblyPlugin.autoImport.assembly

val V = new {
	val Scala = "3.2.0"
	val jakon = "0.5.1-SNAPSHOT"
}
val projectName = "template-app"
val projectVersion = "1.0.0"

scalaVersion := V.Scala
organization := "cz.kamenitxan"
name := projectName
version := projectVersion


ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / resolvers += "Artifactory" at "https://kamenitxans-maven-repository.appspot.com/"


val Dependencies = new {

	lazy val frontend = Seq(

	)

	lazy val backend = Seq(
		libraryDependencies ++=
			Seq(
				"cz.kamenitxan" %% "jakon" % V.jakon changing() excludeAll (
					ExclusionRule(organization = "com.sun.mail", name = "smtp"),
					ExclusionRule(organization = "javax.mail", name = "javax.mail-api")
				),
				"org.scalatest" %% "scalatest" % "3.2.14" % "test",
				"org.seleniumhq.selenium" % "htmlunit-driver" % "3.63.0" % "test"
			)
	)

	lazy val tests = Def.settings(
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
	.enablePlugins(JavaAppPackaging)
	.enablePlugins(DockerPlugin)
	.settings(
		name := projectName,
		Compile / mainClass := Some("cz.kamenitxan.templateapp.Main"),
		Test / fork := true,
		Universal / mappings += {
			val appJs = (frontend / Compile / fullOptJS).value.data
			appJs -> "lib/prod.js"
		},
		Universal / javaOptions ++= Seq(
			"--port 8080",
			"--mode prod"
		),
		Docker / packageName := "tauroadmin-example"
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
