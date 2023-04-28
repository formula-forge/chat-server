package dao.entities

import io.vertx.core.json.*

data class UserEntity(
    var userId:Int? = null,
    var userName:String? = null,
    var userDetail: JsonObject? = null,
    var passWord:String? = null,
    var phone:String? = null,
    var avatar:String? = null,
    var motto:String?= null,
    var protected:Boolean? = null,
    var favFormula : JsonObject? = null
)