package cz.kamenitxan.templateapp.controler

import java.sql.Connection
import java.util

import com.google.gson.{Gson, GsonBuilder}
import cz.kamenitxan.jakon.core.configuration.{DeployMode, Settings}
import cz.kamenitxan.jakon.core.controler.IControler
import cz.kamenitxan.jakon.core.database.DBHelper
import cz.kamenitxan.jakon.core.template.TemplateEngine
import cz.kamenitxan.jakon.core.template.utils.TemplateUtils
import cz.kamenitxan.templateapp.entity.Word
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

/**
  * Created by TPa on 2019-08-24.
  */
class WordsControler extends IControler {
	private val logger: Logger = LoggerFactory.getLogger(this.getClass)

	private val template = "raw"
	private val gson = if (Settings.getDeployMode == DeployMode.DEVEL) {
		new GsonBuilder().setPrettyPrinting().create
	} else {
		new Gson()
	}

	private val ALL_WORDS_SQL = "SELECT * FROM Word JOIN Chapter ON Word.chapter_id = Chapter.id"

	def generate() {
		val e: TemplateEngine = TemplateUtils.getEngine
		implicit val conn: Connection = DBHelper.getConnection
		try {
			val stmt = conn.createStatement()
			val words = DBHelper.selectDeep(stmt, ALL_WORDS_SQL, classOf[Word])
			val context = new util.HashMap[String, AnyRef]
			context.put("content", gson.toJson(words.asJava))
			e.render(template, "words.json", context)
		} catch {
			case ex: Exception => logger.error("Exception occurred while generation of words json", ex)
		} finally {
			conn.close()
		}
	}
}
