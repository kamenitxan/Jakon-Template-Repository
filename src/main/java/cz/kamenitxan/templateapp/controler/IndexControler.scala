package cz.kamenitxan.templateapp.controler

import java.util

import cz.kamenitxan.jakon.core.controller.IController
import cz.kamenitxan.jakon.core.database.DBHelper
import cz.kamenitxan.jakon.core.template.TemplateEngine
import cz.kamenitxan.jakon.core.template.utils.TemplateUtils
import cz.kamenitxan.jakon.logging.Logger


/**
 * Created by TPa on 2019-08-24.
 */
class IndexControler extends IController {
	private val template = "index"
	private val ALL_PAGES_SQL = "SELECT * FROM Chapter JOIN JakonObject ON Chapter.id = JakonObject.id"

	def generate() {
		val e: TemplateEngine = TemplateUtils.getEngine
		val conn = DBHelper.getConnection
		try {
			val stmt = conn.createStatement()

			val context = new util.HashMap[String, AnyRef]
			context.put("chapters", "sada")
			e.render(template, "index.html", context)
		} catch {
			case ex: Exception => Logger.error("Exception occurred while generation of index page", ex)
		} finally {
			conn.close()
		}
	}
}
