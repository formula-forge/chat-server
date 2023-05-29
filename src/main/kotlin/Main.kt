import dao.ConnectionPool
import dao.FriendDao
import dao.UserDao
import dao.entities.UserEntity
import io.vertx.core.Vertx
import utilities.JaxUtility
import verticle.MainVerticle

suspend fun main(args: Array<String>) {
    println("Hello World!")

//    val pool = ConnectionPool.getPool()
////
    val userDao = UserDao()

//    val friendDao = FriendDao()

//    println(UserDao().insertElement(ConnectionPool.getPool(),UserEntity(
//        userName = "testG",
//        passWord = "P@ssW0rd",
//        phone = "19260000000"
//    )))

    val vertx = Vertx.vertx()
//    vertx.deployVerticle(MainVerticle())
    val wsq = userDao.getElementByKey(ConnectionPool.getPool(vertx), 105)
    val jax = JaxUtility(vertx, 2023, "127.0.0.1")
    userDao.updateElementByConditions(
        ConnectionPool.getPool(vertx),
        "id = \$%d",
        UserEntity(
            favFormula = jax.transformFav2(wsq!!.favFormula!!)
        ),
        105
    )
}