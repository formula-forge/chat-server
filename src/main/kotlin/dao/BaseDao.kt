package dao

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import io.vertx.sqlclient.impl.ArrayTuple
import java.awt.RenderingHints.Key
import javax.swing.tree.RowMapper
import kotlin.reflect.KProperty1


abstract class BaseDao <T : Any, TKey : Any> {
    abstract val tableName : String
    abstract fun rowMapper(row : Row) :  T
    abstract val keyName : String
    abstract val primaryKey : KProperty1<T,TKey?>
    abstract val colSize : Int
    abstract val rowMap : Map<String,KProperty1<T,Any?>>

    open suspend fun getElementByKey(connection : PgPool, key : TKey) : T?{
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE \"%s\" = \$1".format(tableName,keyName))
            .execute(Tuple.of(key)).await()
        if (rows.size() == 0)
            return null
        if (rows.size() >= 2)
            throw RuntimeException("Duplicated primary key %s at %s".format(key.toString(),keyName))
        return rowMapper(rows.first())
    }

    open suspend fun getElementByKeys(connection: PgPool, keys : List<TKey>) : Map<TKey,T>{
        val result = HashMap<TKey,T>()
        if (keys.isEmpty())
            return result
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE \"%s\" = ANY($1)".format(tableName, keyName))
            .execute(Tuple.of(keys)).await()

        rows.forEach{ row->
            val entity = rowMapper(row)
            val key = primaryKey.get(entity)
            if (key != null)
                result[key] = entity
        }
        return result
    }

    private fun composeRows(rows : RowSet<Row>) : HashMap<TKey,T>?{
        val result = HashMap<TKey,T>()

        if (rows.size() == 0)
            return null
        for (row in rows){
            val entity = rowMapper(row)
            val key = primaryKey.get(entity)
            if(key!=null)
                result[key] = entity
        }

        return result
    }

    suspend fun getAllElements(connection: PgPool) : Map<TKey,T>? {
        val rows = connection
            .query("SELECT * FROM %s".format(tableName))
            .execute().await()

        return composeRows(rows)
    }

    open suspend fun getElementsByConditions(connection: PgPool, condClause : String, vararg prepared : Any) : Map<TKey,T>? {
        val rows = connection
            .preparedQuery("SELECT * FROM %s WHERE %s".format(tableName,condClause))
            .execute(Tuple.from(prepared)).await()

        return composeRows(rows)
    }

    private fun makeInsertClause(entity : T) : Triple<String,String,Tuple>{
        val cols = StringBuilder("(")
        val values = StringBuilder("(")
        var cnt = 1
        val prepared = ArrayTuple(1 + colSize)
        for(p in rowMap){
            if(p.value.get(entity) != null){
                cols.append(p.key).append(",")
                prepared.addValue(p.value.get(entity))
                values.append("\$").append(cnt++).append(",")
            }
        }
        cols.deleteAt(cols.length - 1).append(")")
        values.deleteAt(values.length - 1).append(")")
        return Triple(cols.substring(0),values.substring(0),prepared)
    }
    suspend fun insertElement(connection: PgPool, entity : T ) : TKey{
        val clause = makeInsertClause(entity)

        val result = connection
            .preparedQuery("INSERT INTO %s %s VALUES %s RETURNING %s".format(tableName,clause.first,clause.second,keyName))
            .execute(clause.third)
            .await()

        return result.first().getValue(keyName) as TKey
    }

    open suspend fun deleteElementByKey(connection: PgPool, key : TKey ) {
        connection
            .preparedQuery("DELETE FROM %s WHERE %s = \$1".format(tableName, keyName))
            .execute(Tuple.of(key))
            .await()
    }

    suspend fun deleteElementsByConditions(connection: PgPool, condClause : String, vararg prepared : Any) {
        connection
            .preparedQuery("DELETE FROM %s WHERE %s".format(tableName,condClause))
            .execute(Tuple.from(prepared))
            .await()
    }

    suspend fun updateElementByConditions(connection: PgPool, condClause: String,  entity: T, vararg condPrepared : Any){
        val updClause : StringBuilder = StringBuilder()

        var cnt = 1
        val valPrepared = ArrayList<Any>()

        for(p in rowMap){
            if(p.value.get(entity) != null){
                updClause.append(p.key).append(" = \$").append(cnt++).append(",")
                valPrepared.add(p.value.get(entity)!!)
            }
        }

        if (valPrepared.isEmpty())
            throw UnsupportedOperationException("No values to update")

        valPrepared.addAll(condPrepared.toList())
        val range : Array<Int> = IntRange(cnt,cnt + condPrepared.size).toList().toTypedArray()
        println("UPDATE %s SET %s WHERE %s"
            .format(tableName,updClause.substring(0,updClause.length-1),
                condClause.format(*range)))

        connection
            .preparedQuery("UPDATE %s SET %s WHERE %s"
                .format(tableName,updClause.substring(0,updClause.length-1),
                    condClause.format(*range)))
            .execute(Tuple.from(valPrepared))
            .await()
    }
}
