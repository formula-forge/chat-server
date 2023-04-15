package dao.entities

import io.vertx.core.json.JsonArray
import java.time.OffsetDateTime


data class GroupEntity (
    var groupId : Int?= null,
    var groupName : String?= null,
    var description : String?= null,
    var memberCount : Short?= null,
    var groupMembers : JsonArray?= null,
    var groupAdmins : Array<String>?= null,
    var registerTime : OffsetDateTime?= null,
    var groupNotices : JsonArray?= null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GroupEntity

        if (groupId != other.groupId) return false
        if (groupName != other.groupName) return false
        if (description != other.description) return false
        if (memberCount != other.memberCount) return false
        if (groupMembers != other.groupMembers) return false
        if (groupAdmins != null) {
            if (other.groupAdmins == null) return false
            if (!groupAdmins.contentEquals(other.groupAdmins)) return false
        } else if (other.groupAdmins != null) return false
        if (registerTime != other.registerTime) return false
        return groupNotices == other.groupNotices
    }

    override fun hashCode(): Int {
        var result = groupId ?: 0
        result = 31 * result + (groupName?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (memberCount ?: 0)
        result = 31 * result + (groupMembers?.hashCode() ?: 0)
        result = 31 * result + (groupAdmins?.contentHashCode() ?: 0)
        result = 31 * result + (registerTime?.hashCode() ?: 0)
        result = 31 * result + (groupNotices?.hashCode() ?: 0)
        return result
    }
}