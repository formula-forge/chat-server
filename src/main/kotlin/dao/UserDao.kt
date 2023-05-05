package dao

import dao.entities.UserEntity
import io.vertx.ext.auth.User
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import javax.swing.tree.RowMapper
import kotlin.reflect.KProperty1

class UserDao : BaseDao<UserEntity, Int>() {
    override val tableName: String = "users"
    override fun rowMapper(row: Row): UserEntity {
        return UserEntity(
            userId = row.getInteger("id"),
            userName = row.getString("username"),
            userDetail = row.getJsonObject("detail"),
            phone = row.getString("phone"),
            passWord = row.getString("password"),
            avatar = row.getString("avatar"),
            motto = row.getString("motto"),
            protected = row.getBoolean("protected"),
            favFormula = row.getJsonObject("fav_formula")
        )
    }

    override val keyName: String = "id"
    override val primaryKey : KProperty1<UserEntity, Int?> = UserEntity::userId
    override val colSize: Int = 8
    override val rowMap: Map<String, KProperty1<UserEntity, Any?>>
        get(){
            val map = HashMap<String, KProperty1<UserEntity, Any?>>()
            map["id"] = UserEntity::userId
            map["username"] = UserEntity::userName
            map["detail"] = UserEntity::userDetail
            map["phone"] = UserEntity::phone
            map["password"] = UserEntity::passWord
            map["avatar"] = UserEntity::avatar
            map["motto"] = UserEntity::motto
            map["protected"] = UserEntity::protected
            map["fav_formula"] = UserEntity::favFormula
            return map
       }

    suspend fun getUsersAvatars(connection: PgPool, id: List<Int>): Map<Int,String> {
        if (id.isEmpty()) return HashMap()
        val rows = connection
            .preparedQuery("SELECT id,avatar FROM %s WHERE %s = ANY(\$1)".format(tableName, keyName))
            .execute(Tuple.of(id.toTypedArray())).await()

        val ret = HashMap<Int, String>()

        rows?.forEach{ row ->
            val userId = row.getInteger(keyName)
            val avatar = row.getString("avatar")
            ret[userId] = avatar
        }

        return ret
    }
}