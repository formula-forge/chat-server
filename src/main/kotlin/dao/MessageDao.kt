package dao

import dao.entities.MessageEntity
import dao.entities.SessionEntity
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.time.LocalDateTime
import kotlin.reflect.KProperty1

class MessageDao : BaseDao<MessageEntity, Int>(){
    override val tableName: String = "message"
    override val keyName: String = "sender"
    override val colSize: Int = 6
    override val primaryKey = MessageEntity::sender

    override fun rowMapper(row: Row): MessageEntity {
        return MessageEntity(
            sender = row.getInteger("sender"),
            receiver = row.getInteger("receiver"),
            type = row.getString("type"),
            time = row.getLocalDateTime("time"),
            group = row.getInteger("group"),
            content = row.getString("content"),
        )
    }

    override val rowMap : Map<String, KProperty1<MessageEntity, Any?>>
        get(){
            val map = HashMap<String, KProperty1<MessageEntity, Any?>>()
            map["sender"] = MessageEntity::sender
            map["receiver"] = MessageEntity::receiver
            map["type"] = MessageEntity::type
            map["time"] = MessageEntity::time
            map["group"] = MessageEntity::group
            map["content"] = MessageEntity::content
            return map
        }

    override suspend fun getElementByKey(connection: PgPool, key: Int): MessageEntity? {
        throw UnsupportedOperationException("Unsupported key type")
    }

    private fun composeRows(rows : RowSet<Row>) : List<MessageEntity>?{
        val result = ArrayList<MessageEntity>()

        if (rows.size() == 0)
            return null
        for (row in rows){
            val entity = rowMapper(row)
            result.add(entity)
        }

        return result
    }

    suspend fun getElements(connection: PgPool, condClause : String, vararg prepared : Any) : List<MessageEntity>? {
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE %s".format(tableName,condClause))
            .execute(Tuple.from(prepared)).await()

        return composeRows(rows)
    }

    suspend fun getElementByKey(connection: PgPool, sender: Int, receiver: Int, time : LocalDateTime): MessageEntity? {
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE sender = \$1 AND receiver = \$2 AND time = \$3".format(tableName))
            .execute(Tuple.of(sender, receiver, time)).await()
        if (rows.size() == 0)
            return null
        return rowMapper(rows.first())
    }

    override suspend fun deleteElementByKey(connection: PgPool, key: Int) {
        throw UnsupportedOperationException("Unsupported key type")
    }
}