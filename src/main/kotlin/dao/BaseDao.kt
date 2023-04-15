package dao

import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import io.vertx.core.Future
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.impl.ArrayTuple
import kotlin.reflect.*


abstract class BaseDao <T : Any, TKey : Any> {
    abstract val tableName : String
    abstract fun rowMapper(row : Row) :  T
    abstract val keyName : String
    abstract val primaryKey : KProperty1<T,TKey?>
    abstract val colSize : Int
    abstract val rowMap : Map<String,KProperty1<T,Any?>>

    fun getElementByKey(connection : PgPool, key : TKey) : Future<T?>{
        return connection
            .preparedQuery("SELECT * FROM %s WHERE %s = $1".format(tableName,keyName))
            .execute(Tuple.of(key))
            .compose(
                fun(rows : RowSet<Row>) : Future<T?>{
                    if (rows.size() == 0)
                        return Future.succeededFuture<T?>(null)
                    if (rows.size() >= 2)
                        return Future.failedFuture<T?>("Duplicated primary key at %s\".format(keyName)")
                    val row = rows.first()
                    val result  = rowMapper(row)
                    return Future.succeededFuture<T?>(result)
                },
                fun(exception : Throwable) : Future<T?> {
                    return Future.failedFuture<T?>(exception);
                }
            )
    }

    fun getAllElements(connection: PgPool) : Future<Map<TKey,T>?> {
        return connection
            .query("SELECT * FROM %s".format(tableName))
            .execute()
            .compose(
                fun(rows : RowSet<Row>) : Future<Map<TKey,T>?> {
                    val res = HashMap<TKey,T>()
                    if (rows.size() == 0)
                        return Future.succeededFuture<Map<TKey,T>?>(null)
                    rows.forEach {
                        val entity = rowMapper(it)
                        val key = primaryKey.get(entity)
                        if(key!=null)
                            res[key] = entity
                    }
                    return Future.succeededFuture<Map<TKey,T>?>(res)
                },
                fun(exception : Throwable) : Future<Map<TKey,T>?> {
                    return Future.failedFuture<Map<TKey,T>?>(exception);
                }
            )
    }

    fun getElementsByConditions(connection: PgPool, condClause : String, vararg prepared : Any) : Future<Map<TKey,T>?> {
        return connection
            .preparedQuery("SELECT * FROM %s WHERE %s".format(tableName,condClause))
            .execute(Tuple.from(prepared))
            .compose(
                fun(rows : RowSet<Row>) : Future<Map<TKey,T>?> {
                    val res = HashMap<TKey,T>()
                    if (rows.size() == 0)
                        return Future.succeededFuture<Map<TKey,T>?>(null)
                    rows.forEach {
                        val entity = rowMapper(it)
                        val key = primaryKey.get(entity)
                        if(key!=null)
                            res[key] = entity
                    }
                    return Future.succeededFuture<Map<TKey,T>?>(res)
                },
                fun(exception : Throwable) : Future<Map<TKey,T>?> {
                    return Future.failedFuture<Map<TKey,T>?>(exception);
                }
            )
    }

    fun insertElement(connection: PgPool, entity : T ) : Future<Nothing> {
        var cols : String = "("
        var vals : String = "("
        var cnt = 1
        val prepared = ArrayTuple(1 + colSize)
        for(p in rowMap){
            if(p.value.get(entity) != null){
                cols += p.key + ","
                prepared.addValue(p.value.get(entity))
                vals += "\$%d,".format(cnt++)
            }

        }
        cols = cols.substring(0,cols.length - 1)
        vals = vals.substring(0,vals.length - 1)
        cols += ')'
        vals += ')'

//        println("INSERT INTO %s %s VALUES %s".format(tableName,cols,vals))

        return connection
            .preparedQuery("INSERT INTO %s %s VALUES %s".format(tableName,cols,vals))
            .execute(prepared)
            .compose(
                fun(rows : RowSet<Row>) : Future<Nothing>{
                    return Future.succeededFuture()
                },
                fun(exception : Throwable) : Future<Nothing> {
                    return Future.failedFuture<Nothing>(exception)
                }
            )
    }

    fun deleteElementByKey(connection: PgPool, key : TKey ) : Future<Nothing> {
        return connection
            .preparedQuery("DELETE FROM %s WHERE %s = $1".format(tableName, keyName))
            .execute(Tuple.of(key))
            .compose(
                fun(rows : RowSet<Row>) : Future<Nothing>{
                    return Future.succeededFuture()
                },
                fun(exception : Throwable) : Future<Nothing> {
                    return Future.failedFuture<Nothing>(exception);
                }
            )
    }

    fun updateElementByConditions(connection: PgPool, condClause: String,  entity: T, vararg condPrepared : Any) : Future<Nothing> {
        var updClause : StringBuilder = StringBuilder()

        var cnt = 1
        val valPrepared = ArrayList<Any>()

        for(p in rowMap){
            if(p.value.get(entity) != null){
                updClause.append(p.key).append(" = \$").append(cnt++).append(",")
                valPrepared.add(p.value.get(entity)!!)
            }
        }

        valPrepared.addAll(condPrepared.toList())
        val range : Array<Int> = IntRange(cnt,cnt + condPrepared.size).toList().toTypedArray()
        println("UPDATE %s SET %s WHERE %s"
            .format(tableName,updClause.substring(0,updClause.length-1),
                condClause.format(*range)))

        return connection
            .preparedQuery("UPDATE %s SET %s WHERE %s"
                .format(tableName,updClause.substring(0,updClause.length-1),
                    condClause.format(*range)))
            .execute(Tuple.from(valPrepared))
            .compose(
                fun(rows : RowSet<Row>) : Future<Nothing>{
                    return Future.succeededFuture()
                },
                fun(exception : Throwable) : Future<Nothing> {
                    return Future.failedFuture<Nothing>(exception);
                }
            )

    }
}
