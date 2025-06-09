package cz.kamenitxan.templateapp.controler

import cz.kamenitxan.jakon.core.controller.IController
import cz.kamenitxan.jakon.core.database.DBHelper
import cz.kamenitxan.jakon.core.template.TemplateEngine
import cz.kamenitxan.jakon.core.template.utils.TemplateUtils


/**
 * Created by TPa on 2019-08-24.
 */
class IndexControler extends IController {
	private val template = "index"
	private val ALL_PAGES_SQL = "SELECT * FROM Chapter JOIN JakonObject ON Chapter.id = JakonObject.id"

	def generate(): Unit = {
		val e: TemplateEngine = TemplateUtils.getEngine
		
		DBHelper.withDbConnection(implicit conn => {
			val context = Map[String, AnyRef](
				"chapters" -> "chapter list"
			)
			e.render(template, "index.html", context)
		})
		
	}
}
