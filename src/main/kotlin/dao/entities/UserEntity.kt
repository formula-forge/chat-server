package dao.entities

import io.vertx.core.json.*
import java.time.LocalDateTime
import java.time.OffsetDateTime


data class UserEntity(
    var userId:Int?,
    var userName:String?,
    var userDetail: JsonObject?,
    var friendList:JsonArray?,
    var groupList:JsonArray?,
    var registerTime:OffsetDateTime?,
    var passWord:String?,
    var telephone:String?
)