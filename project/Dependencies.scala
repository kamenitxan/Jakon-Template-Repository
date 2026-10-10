import sbt.*
import sbt.Keys.*

object V {
	val Scala = "3.3.7"
	val jakon = "0.7.1"
}

object Dependencies {

	lazy val backend: Seq[Def.Setting[?]] = Seq(
		libraryDependencies ++=
			Seq(
				("cz.kamenitxan" %% "jakon" % V.jakon).changing()
			)
	)

	lazy val tests: Seq[Def.Setting[?]] = Def.settings(
		libraryDependencies ++= Seq(
			"org.scalatest" %% "scalatest" % "3.2.19" % Test,
			"org.seleniumhq.selenium" % "htmlunit3-driver" % "4.41.0" % Test
		)
	)
}
