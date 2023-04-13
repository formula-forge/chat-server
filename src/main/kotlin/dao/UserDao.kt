package dao

import dao.entities.UserEntity
import io.vertx.sqlclient.Row
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KCallable
import kotlin.reflect.KProperty1

class UserDao : BaseDao<UserEntity, Int>() {
    override val tableName: String = "users"
    override fun rowMapper(row: Row): UserEntity {
        return UserEntity(
            userId = row.getInteger("userid"),
            userName = row.getString("username"),
            userDetail = row.getJsonObject("detail"),
            friendList = row.getJsonArray("friendlist"),
            groupList = row.getJsonArray("grouplist"),
            registerTime = row.getOffsetDateTime("register_time") ,
            telephone = row.getString("telephone"),
            passWord = row.getString("password")
        )
    }

    override val keyName: String = "userid"
    override val primaryKey : KProperty1<UserEntity, Int?> = UserEntity::userId
    override val colSize: Int = 8
    override val rowMap: Map<String, KProperty1<UserEntity, Any?>>
        get(){
            val map = HashMap<String, KProperty1<UserEntity, Any?>>()
            map["userid"] = UserEntity::userId
            map["userName"] = UserEntity::userName
            map["detail"] = UserEntity::userDetail
            map["friendlist"] = UserEntity::friendList
            map["grouplist"] = UserEntity::groupList
            map["register_time"] = UserEntity::registerTime
            map["telephone"] = UserEntity::telephone
            map["password"] = UserEntity::passWord
            return map
        }
}