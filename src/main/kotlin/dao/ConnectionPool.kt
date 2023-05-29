package dao

import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import io.vertx.pgclient.PgConnectOptions
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
                        .setPassword("Z2PmAavmuOLqsf05xLF6")
    ) {
        val logger = LoggerFactory.getLogger(this::class.java)

        logger.info("Connecting to the database...")

        try {
            val poolOptions : PoolOptions = PoolOptions().setMaxSize(20)
            pool = PgPool.pool(vertx, connectOptions, poolOptions)
        } catch (e: Exception) {
            logger.error("Failed to connect the database.", e)
        }
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