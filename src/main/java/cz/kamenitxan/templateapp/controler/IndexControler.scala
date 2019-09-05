package cz.kamenitxan.vocabularomana.controler

import java.util

import cz.kamenitxan.jakon.core.controler.IControler
import cz.kamenitxan.jakon.core.database.DBHelper
import cz.kamenitxan.jakon.core.template.TemplateEngine
import cz.kamenitxan.jakon.core.template.utils.TemplateUtils
import cz.kamenitxan.vocabularomana.entity.Chapter
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

/**
  * Created by TPa on 2019-08-24.
  */
class IndexControler extends IControler {
	private val logger: Logger = LoggerFactory.getLogger(this.getClass)
	private val template = "index"
	private val ALL_PAGES_SQL = "SELECT * FROM Chapter JOIN JakonObject ON Chapter.id = JakonObject.id"

	def generate() {
		val e: TemplateEngine = TemplateUtils.getEngine
		val conn = DBHelper.getConnection
		try {
			val stmt = conn.createStatement()
			val chapters = DBHelper.select(stmt, ALL_PAGES_SQL, classOf[Chapter]).map(qr => qr.entity)
			val context = new util.HashMap[String, AnyRef]
			context.put("chapters", chapters.asJava)
			e.render(template, "index.html", context)
		} catch {
			case ex: Exception => logger.error("Exception occurred while generation of index page", ex)
		} finally {
			conn.close()
		}
	}
}
