package utilities

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

object ServerUtility {
    fun responseError(context: RoutingContext ,status : Int, error : Int, message : String) {
        context.response().putHeader("Content-Type", "application/json")
        context.response().setStatusCode(status).end(
            json {
                obj(
                    "status" to status,
                    "code" to error,
                    "message" to message
                )
            }.encode()
        )
    }

    fun responseSuccess(context: RoutingContext ,status : Int, data: JsonObject? = null) {
        val res = json {
            obj(
                "status" to status,
                "message" to "OK"
            )
        }

        if (data!=null){
            res.mergeIn(data)
        }

        context.response().setStatusCode(status).end(
            res.encode()
        )
    }

    fun checkNull(context : RoutingContext, vararg args : Any?) : Boolean {
        for (arg in args) {
            if (arg == null) {
                responseError(context, 400, 1, "参数不完整")
                return true
            }
        }
        return false
    }
}