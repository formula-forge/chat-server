package dao

import dao.entities.UserEntity
import io.vertx.sqlclient.Row
import kotlin.reflect.KProperty1

class UserDao : BaseDao<UserEntity, Int>() {
    override val tableName: String = "users"
    override fun rowMapper(row: Row): UserEntity {
        return UserEntity(
            userId = row.getInteger("userId"),
            userName = row.getString("username"),
            userDetail = row.getJsonObject("detail"),
            friendList = row.getJsonArray("friendlist"),
            groupList = row.getJsonArray("grouplist"),
            phone = row.getString("phone"),
            passWord = row.getString("password"),
            avatar = row.getString("avatar"),
            motto = row.getString("motto")
        )
    }

    override val keyName: String = "userid"
    override val primaryKey : KProperty1<UserEntity, Int?> = UserEntity::userId
    override val colSize: Int = 8
    override val rowMap: Map<String, KProperty1<UserEntity, Any?>>
        get(){
            val map = HashMap<String, KProperty1<UserEntity, Any?>>()
            map["userId"] = UserEntity::userId
            map["username"] = UserEntity::userName
            map["detail"] = UserEntity::userDetail
            map["friendlist"] = UserEntity::friendList
            map["grouplist"] = UserEntity::groupList
            map["phone"] = UserEntity::phone
            map["password"] = UserEntity::passWord
            map["avatar"] = UserEntity::avatar
            map["motto"] = UserEntity::motto
            return map
        }
}