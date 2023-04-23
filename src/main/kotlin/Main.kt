import dao.ConnectionPool
import dao.FriendDao
import io.vertx.core.Vertx
import verticle.MainVerticle

suspend fun main(args: Array<String>) {
    println("Hello World!")

//    val pool = ConnectionPool.getPool()
////
////    val userDao = UserDao()
//
//    val friendDao = FriendDao()

    val vertx = Vertx.vertx()
    vertx.deployVerticle(MainVerticle())
}