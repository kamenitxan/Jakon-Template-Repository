package test

import com.google.gson.Gson
import cz.kamenitxan.jakon.core.configuration.Settings
import cz.kamenitxan.jakon.core.dynamic.entity.{AbstractJsonResponse, ResponseStatus}
import org.scalatest.funsuite.AnyFunSuite

/**
 * Created by TPa on 19.02.2022.
 */
class ApiTestBase extends AnyFunSuite {

	val gson = new Gson()

	lazy val host: String = "http://localhost:" + Settings.getPort + "/api"
	val count = "/count"
	val create = "/create"
	val edit = "/edit"
	val single = "/single"
	val all = "/all"
	val delete = "/delete"

	def assertResponse(res: AbstractJsonResponse[_ <: Any]): Unit = {
		assertEmptyResponse(res)
		assert(res.data != null)
	}

	def assertRawResponse(res: String, status: ResponseStatus = ResponseStatus.success): Unit = {
		assert(res != null)
		assert(res.contains(status.name()))
	}

	def assertEmptyResponse(res: AbstractJsonResponse[_ <: Any]): Unit = {
		assert(res != null)
		assert(res.status == ResponseStatus.success)
	}

}