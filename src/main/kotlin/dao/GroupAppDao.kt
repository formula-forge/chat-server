package dao

import dao.entities.GroupAppEntity
import io.vertx.sqlclient.Row
import kotlin.reflect.KProperty1

class GroupAppDao : BaseDao<GroupAppEntity, Int>() {
    override val tableName: String = "group_app"
    override val keyName: String = "id"
    override val primaryKey: KProperty1<GroupAppEntity, Int?> = GroupAppEntity::id
    override val colSize: Int = 6

    override val rowMap: Map<String, KProperty1<GroupAppEntity, Any?>>
        get(){
            val map = HashMap<String, KProperty1<GroupAppEntity, Any?>>()
            map["id"] = GroupAppEntity::id
            map["sender"] = GroupAppEntity::sender
            map["group"] = GroupAppEntity::group
            map["approved"] = GroupAppEntity::approved
            map["message"] = GroupAppEntity::message
            map["created_at"] = GroupAppEntity::createdAt
            return map
        }

    override fun rowMapper(row: Row): GroupAppEntity {
        return GroupAppEntity(
            id = row.getInteger("id"),
            sender = row.getInteger("sender"),
            group = row.getInteger("receiver"),
            approved = row.getBoolean("approved"),
            message = row.getString("message"),
            createdAt = row.getLocalDate("created_at"),
        )
    }
}