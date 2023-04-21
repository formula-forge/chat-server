package dao.entities

import io.vertx.core.json.*

data class UserEntity(
    var userId:Int? = null,
    var userName:String? = null,
    var userDetail: JsonObject? = null,
    var friendList:JsonArray? = null,
    var groupList:JsonArray? = null,
    var passWord:String? = null,
    var phone:String? = null,
    var avatar:String? = null,
    val motto: String?= null
)