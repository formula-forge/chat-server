package dao

import dao.entities.MessageEntity
import dao.entities.SessionEntity
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import kotlin.reflect.KProperty1

class SessionDao : BaseDao<SessionEntity, Int>() {
    override val tableName: String = "session"
    override val keyName: String = "target"
    override val colSize: Int = 6
    override val primaryKey = SessionEntity::userId

    override fun rowMapper(row: Row): SessionEntity {
        return SessionEntity(
            userId = row.getInteger("id"),
            target = row.getInteger("target"),
            unread = row.getInteger("unread"),
            latest = row.getLocalDateTime("latest"),
            group = row.getBoolean("group"),
            latest_msg = row.getString("latest_msg"),
            hidden = row.getBoolean("hidden"),
            expire = row.getLocalDateTime("expire")
        )
    }

    override val rowMap: Map<String, KProperty1<SessionEntity, Any?>> = mapOf(
        "id" to SessionEntity::userId,
        "target" to SessionEntity::target,
        "unread" to SessionEntity::unread,
        "latest" to SessionEntity::latest,
        "group" to SessionEntity::group,
        "latest_msg" to SessionEntity::latest_msg,
        "hidden" to SessionEntity::hidden,
        "expire" to SessionEntity::expire
    )

    override suspend fun getElementByKey(connection: PgPool, key: Int): SessionEntity? {
        throw UnsupportedOperationException("Unsupported key type")
    }

    override suspend fun deleteElementByKey(connection: PgPool, key: Int) {
        throw UnsupportedOperationException("Unsupported key type")
    }

    suspend fun deleteElementByKey(connection: PgPool, userId: Int, target: Int) {
        connection
            .preparedQuery("DELETE FROM %s WHERE id = \$1 AND target = \$2".format(tableName))
            .execute(Tuple.of(userId, target)).await()
    }

    private fun composeRows(rows : RowSet<Row>) : List<SessionEntity>?{
        val result = ArrayList<SessionEntity>()

        if (rows.size() == 0)
            return null
        for (row in rows){
            val entity = rowMapper(row)
            result.add(entity)
        }

        return result
    }

    suspend fun getElements(connection: PgPool, condClause : String, vararg prepared : Any) : List<SessionEntity>? {
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE %s".format(tableName,condClause))
            .execute(Tuple.from(prepared)).await()

        return composeRows(rows)
    }

    suspend fun getElementByKey(connection: PgPool, userId: Int, target: Int): SessionEntity? {
        val result = connection
            .preparedQuery("SELECT * FROM %s WHERE id = \$1 AND target = \$2".format(tableName))
            .execute(Tuple.of(userId, target)).await()
        return if (result.size() == 0) null else rowMapper(result.first())
    }

}