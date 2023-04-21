package dao

import io.vertx.core.Vertx
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgConnection
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions

object ConnectionPool {
    private var pool : PgPool? = null;
    fun connect(connectOptions: PgConnectOptions =
                    PgConnectOptions()
                        .setPort(5432)
                        .setHost("192.168.2.3")
                        .setDatabase("formula-alchemy")
                        .setUser("chatex")
                        .setPassword("6WS+EgWG3wYoffqI")
    ) {
        val poolOptions : PoolOptions = PoolOptions().setMaxSize(20)
        pool = PgPool.pool(connectOptions, poolOptions)
    }

    fun getPool() : PgPool{
        if (pool == null){
            connect()
        }
        if (pool == null){
            throw Exception("Failed to connect the database.")
        }
        else{
            return pool!!;
        }
    }
}