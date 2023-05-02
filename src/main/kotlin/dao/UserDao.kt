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

    suspend fun getUsersById(connection: PgPool, id: List<Int>): Map<Int,UserEntity> {
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE %s = $1".format(tableName, keyName))
            .executeBatch(id.map { Tuple.of(it) }).await()

        val ret = HashMap<Int, UserEntity>()

        rows?.forEach{ row ->
            val user = rowMapper(row)

            ret[user.userId!!] = user
        }

        return ret
    }
}