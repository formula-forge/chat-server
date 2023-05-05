package dao

import dao.entities.FriendEntity
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple

class FriendDao{
    suspend fun checkFriendShip(connection : PgPool, user : Int, friend : Int) : Boolean {
        val rows = connection
            .preparedQuery("SELECT * FROM friend WHERE master = $1 AND slave = $2")
            .execute(Tuple.of(user, friend)).await()
        return rows.size() > 0
    }

    suspend fun listFriends(connection : PgPool, user : Int) : List<FriendEntity> {
        val rows = connection
            .preparedQuery("SELECT slave,nickname,classification FROM friend WHERE master = $1")
            .execute(Tuple.of(user)).await()
        val result = ArrayList<FriendEntity>()

        val queryList = StringBuilder()
        queryList.append('(')

        for (row in rows){
            val id = row.getInteger("slave")
            queryList.append(id).append(',')
            result.add(
                FriendEntity(
                    id,
                    row.getString("classification"),
                    null,
                    row.getString("nickname"),
                    null
                )
            )
        }

        val friendAsUsers = UserDao().getElementsByConditions(connection, "id IN ${queryList.substring(0, queryList.length - 1)})")

        for (i in result){
            val id = i.userId!!
            i.name = friendAsUsers?.get(id)?.userName
            i.avatar = friendAsUsers?.get(id)?.avatar
        }

        return result
    }

    suspend fun getFriends(connection: PgPool, user : Int, friend: List<Int>) : Map<Int,FriendEntity> {
        if (friend.isEmpty())
            return HashMap()

        val queries = listOf(
            Tuple.of(77,65),
            Tuple.of(77,85)
        )

        val rows = connection
            .preparedQuery("SELECT * FROM friend WHERE master = $1 AND slave = ANY($2)")
            .execute(Tuple.of(user, friend.toTypedArray())).await()

        val result = HashMap<Int, FriendEntity>()

        rows?.forEach { row->
            val friendEntity = FriendEntity(
                userId = row.getInteger("slave"),
                classification = row.getString("classification"),
                nickname = row.getString("nickname")
            )
            result[friendEntity.userId!!] = friendEntity
        }

        return result
    }

    suspend fun addFriend(connection : PgPool, user : Int, friend : Int, classification : String? = null,
                          nickname : String? = null, reClass : String? = null, reNickname : String? = null) {
        var sql = "INSERT INTO friend (master, slave) VALUES ($1, $2)"
        var tuple = Tuple.of(user, friend)
        if (classification == null && nickname != null){
            sql = "INSERT INTO friend (master, slave, nickname) VALUES ($1, $2, $3)"
            tuple.addValue(nickname)
        }

        else if (classification != null && nickname == null) {
            sql = "INSERT INTO friend (master, slave, classification) VALUES ($1, $2, $3)"
            tuple.addValue(classification)
        }

        else if (classification != null && nickname != null) {
            sql = "INSERT INTO friend (master, slave, classification, nickname) VALUES ($1, $2, $3, $4)"
            tuple.addValue(classification)
            tuple.addValue(nickname)
        }

        connection
            .preparedQuery(sql)
            .execute(tuple).await()

        tuple = Tuple.of(friend, user)
        if (reClass == null && reNickname != null){
            sql = "INSERT INTO friend (master, slave, nickname) VALUES ($1, $2, $3)"
            tuple.addValue(reNickname)
        }

        else if (reClass != null && reNickname == null) {
            sql = "INSERT INTO friend (master, slave, classification) VALUES ($1, $2, $3)"
            tuple.addValue(reClass)
        }

        else if (reClass != null && reNickname != null) {
            sql = "INSERT INTO friend (master, slave, classification, nickname) VALUES ($1, $2, $3, $4)"
            tuple.addValue(reClass)
            tuple.addValue(reNickname)
        }

        connection
            .preparedQuery(sql)
            .execute(tuple).await()
    }

    suspend fun delFriend(connection: PgPool, user: Int, friend: Int){
        connection
            .preparedQuery("DELETE FROM friend WHERE master = $1 AND slave = $2")
            .execute(Tuple.of(user, friend)).await()
        connection
            .preparedQuery("DELETE FROM friend WHERE master = $1 AND slave = $2")
            .execute(Tuple.of(friend, user)).await()
    }

    suspend fun updateFriend(connection: PgPool, user: Int, friend: Int, classification: String? = null, nickname: String? = null){
        if (classification == null && nickname == null){
            throw UnsupportedOperationException("No values to update")
        }

        var sql = "UPDATE friend SET (classification, nickname) = ($1, $2) WHERE master = $3 AND slave = $4"
        var tuple = Tuple.of(classification, nickname)

        if (classification == null){
            sql = "UPDATE friend SET nickname = $1 WHERE master = $2 AND slave = $3"
            tuple = Tuple.of(nickname)
        }

        else if (nickname == null){
            sql = "UPDATE friend SET classification = $1 WHERE master = $2 AND slave = $3"
            tuple = Tuple.of(classification)
        }

        tuple.addValue(user)
        tuple.addValue(friend)

        connection
            .preparedQuery(sql)
            .execute(tuple).await()
    }
}