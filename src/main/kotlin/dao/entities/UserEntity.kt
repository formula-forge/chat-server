package dao.entities

import io.vertx.core.json.*
import java.time.LocalDateTime
import java.time.OffsetDateTime


data class UserEntity(
    var userId:Int? = null,
    var userName:String? = null,
    var userDetail: JsonObject? = null,
    var friendList:JsonArray? = null,
    var groupList:JsonArray? = null,
    var registerTime:OffsetDateTime? = null,
    var passWord:String? = null,
    var telephone:String? = null
)