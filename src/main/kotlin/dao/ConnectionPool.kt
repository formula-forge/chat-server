package dao

import io.vertx.core.Vertx
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgConnection
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions

object ConnectionPool {
    private var pool : PgPool? = null

    fun connect(
        vertx: Vertx?,
        connectOptions: PgConnectOptions =
                    PgConnectOptions()
                        .setPort(5432)
                        .setHost("172.17.182.41")
                        .setDatabase("formula-alchemy")
                        .setUser("chatex")
                        .setPassword("6WS+EgWG3wYoffqI")
    ) {
        val poolOptions : PoolOptions = PoolOptions().setMaxSize(20)
        pool = PgPool.pool(vertx, connectOptions, poolOptions)
    }

    fun getPool(vertx: Vertx? = null) : PgPool{
        if (pool == null){
            connect(vertx)
        }
        if (pool == null){
            throw Exception("Failed to connect the database.")
        }
        else{
            return pool!!
        }
    }
}