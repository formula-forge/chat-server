package dao

import dao.entities.GroupEntity
import dao.entities.UserEntity
import io.vertx.sqlclient.Row
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KCallable
import kotlin.reflect.KProperty1

class GroupDao : BaseDao<GroupEntity, Int>() {
    override val tableName: String = "groups"
    override fun rowMapper(row: Row): GroupEntity {
        return GroupEntity(
            groupId = row.getInteger("groupid"),
            groupName = row.getString("groupname"),
            description = row.getString("descrip"),
            memberCount = row.getShort("member_count"),
            groupMembers = row.getJsonArray("members"),
            groupAdmins = row.getArrayOfStrings("admins"),
            registerTime = row.getOffsetDateTime("register_time") ,
            groupNotices = row.getJsonArray("notice")
        )
    }

    override val keyName: String = "userid"
    override val primaryKey : KProperty1<GroupEntity, Int?> = GroupEntity::groupId
    override val colSize: Int = 8
    override val rowMap: Map<String, KProperty1<GroupEntity, Any?>>
        get(){
            val map = HashMap<String, KProperty1<GroupEntity, Any?>>()
            map["groupid"] = GroupEntity::groupId
            map["groupname"] = GroupEntity::groupName
            map["descrip"] = GroupEntity::description
            map["member_count"] = GroupEntity::memberCount
            map["members"] = GroupEntity::groupMembers
            map["register_time"] = GroupEntity::registerTime
            map["admins"] = GroupEntity::groupAdmins
            map["notice"] = GroupEntity::groupNotices
            return map
        }
}