import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import cz.kamenitxan.jakon.core.Director
import cz.kamenitxan.jakon.core.configuration.Settings
import cz.kamenitxan.jakon.logging.Logger
import cz.kamenitxan.orientfarm.JakonApp
import org.scalatest.{BeforeAndAfterAll, Suites}
import scalikejdbc.*

import java.io.{File, IOException}

/**
 * Created by TPa on 19.02.2022.
 */
class TestRunner extends Suites(

) with BeforeAndAfterAll {

	val config = "jakonConfig=jakon_config_test.properties"

	override def beforeAll(): Unit = {
		new File("jakonUnitTest.sqlite").delete()
		println("Before!")
		Director.init()

		val app = new JakonApp
		try {
			app.run(Array[String](config))
		} catch {
			case _: IOException =>
				app.run(Array[String](config, s"port=${(Settings.getPort + 1).toString}"))
		}

		Thread.sleep(1000)
		Director.render()

	}

}