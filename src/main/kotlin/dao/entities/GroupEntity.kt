package dao.entities

import io.vertx.core.json.JsonArray


data class GroupEntity (
    var groupId : Int?= null,
    var name : String?= null,
    var description : String?= null,
    var memberCount : Short?= null,
    var groupNotices : JsonArray?= null,
    var avatar : String?= null,
    var owner : Int?= null,
    var protected: Boolean? = null
)