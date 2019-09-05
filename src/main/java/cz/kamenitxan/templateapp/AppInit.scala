package cz.kamenitxan.vocabularomana

import cz.kamenitxan.jakon.JakonInit
import cz.kamenitxan.jakon.core.Director
import cz.kamenitxan.jakon.core.database.DBHelper
import cz.kamenitxan.vocabularomana.controler.{IndexControler, WordsControler}
import cz.kamenitxan.vocabularomana.entity.{Chapter, Word}

/**
  * Created by TPa on 2019-08-24.
  */
class AppInit extends JakonInit {
	override def daoSetup(): Unit = {
		DBHelper.addDao(classOf[Word])

		Director.registerControler(new IndexControler)
		Director.registerControler(new WordsControler)
	}


}