package dao

import dao.entities.FriendAppEntiiy
import io.vertx.sqlclient.Row
import kotlin.reflect.KProperty1

class FriendAppDao : BaseDao<FriendAppEntiiy, Int>() {
    override val tableName: String = "friend_app"
    override val keyName: String = "id"
    override val primaryKey: KProperty1<FriendAppEntiiy, Int?> = FriendAppEntiiy::id
    override val colSize: Int = 8

    override val rowMap: Map<String, KProperty1<FriendAppEntiiy, Any?>>
        get(){
            val map = HashMap<String, KProperty1<FriendAppEntiiy, Any?>>()
            map["id"] = FriendAppEntiiy::id
            map["sender"] = FriendAppEntiiy::sender
            map["receiver"] = FriendAppEntiiy::receiver
            map["approved"] = FriendAppEntiiy::approved
            map["message"] = FriendAppEntiiy::message
            map["created_at"] = FriendAppEntiiy::createdAt
            map["classification"] = FriendAppEntiiy::classification
            map["nickname"] = FriendAppEntiiy::nickname
            return map
        }

    override fun rowMapper(row: Row): FriendAppEntiiy {
        return FriendAppEntiiy(
            id = row.getInteger("id"),
            sender = row.getInteger("sender"),
            receiver = row.getInteger("receiver"),
            approved = row.getBoolean("approved"),
            message = row.getString("message"),
            createdAt = row.getLocalDate("created_at"),
            nickname = row.getString("nickname"),
            classification = row.getString("classification")
        )
    }
}