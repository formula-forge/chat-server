import dao.ConnectionPool
import dao.UserDao
import dao.entities.UserEntity
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.time.OffsetDateTime

suspend fun main(args: Array<String>) {
    println("Hello World!")

    val pool = ConnectionPool.getPool()

    val userDao = UserDao()


}