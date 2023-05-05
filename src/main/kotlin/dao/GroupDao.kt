package dao

import dao.entities.GroupEntity
import io.vertx.sqlclient.Row
import kotlin.reflect.KProperty1

class GroupDao : BaseDao<GroupEntity, Int>() {
    override val tableName: String = "groups"
    override fun rowMapper(row: Row): GroupEntity {
        return GroupEntity(
            groupId = row.getInteger("id"),
            name = row.getString("name"),
            description = row.getString("description"),
            groupNotices = row.getJsonArray("notice"),
            avatar = row.getString("avatar"),
            owner = row.getInteger("owner"),
            protected = row.getBoolean("protected"),
            memberCount = row.getShort("member_count")
        )
    }

    override val keyName: String = "id"
    override val primaryKey : KProperty1<GroupEntity, Int?> = GroupEntity::groupId
    override val colSize: Int = 8
    override val rowMap: Map<String, KProperty1<GroupEntity, Any?>> = mapOf(
                "id" to GroupEntity::groupId,
                "name" to GroupEntity::name,
                "description" to GroupEntity::description,
                "notice" to GroupEntity::groupNotices,
                "avatar" to GroupEntity::avatar,
                "owner" to GroupEntity::owner,
                "protected" to GroupEntity::protected,
                "member_count" to GroupEntity::memberCount
            )
}