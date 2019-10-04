package cz.kamenitxan.templateapp.entity

import java.sql.{Connection, Statement}

import cz.kamenitxan.jakon.core.model.JakonObject
import cz.kamenitxan.jakon.webui.ObjectSettings
import cz.kamenitxan.jakon.webui.entity.JakonField

/**
  * Created by TPa on 2019-08-24.
  */
class Word extends JakonObject(classOf[Word].getName) {

	@JakonField(searched = true)
	var latin: String = _
	@JakonField(searched = true)
	var cz: String = _
	@JakonField(searched = true)
	var en: String = _

	override def createObject(jid: Int, conn: Connection): Int = {
		val sql = "INSERT INTO Word (id, latin, cz, en) VALUES (?, ?, ?, ?)"
		val stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
		stmt.setInt(1, jid)
		stmt.setString(2, latin)
		stmt.setString(3, cz)
		stmt.setString(4, en)
		executeInsert(stmt)
	}

	override def updateObject(jid: Int, conn: Connection): Unit = {
		val sql = "UPDATE Word SET latin = ?, cz = ?, en = ? WHERE id = ?"
		val stmt = conn.prepareStatement(sql)
		stmt.setString(1, latin)
		stmt.setString(2, cz)
		stmt.setString(3, en)
		stmt.setInt(4, id)
		stmt.executeUpdate()
	}

	override val objectSettings: ObjectSettings = null
}
