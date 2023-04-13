import dao.ConnectionPool
import dao.UserDao
import dao.entities.UserEntity
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.time.OffsetDateTime

fun main(args: Array<String>) {
    println("Hello World!")

    val pool = ConnectionPool.getPool()

//    pool.query("SELECT * FROM users;").execute().onSuccess {
//        println("Success")
//        println(it.size())
//        it.forEach {
//            println(it.toJson().toString())
//        }
//    }
//        .onFailure {
//            println(it.message)
//        }

    val userdao = UserDao()

    userdao.insertElement(pool, UserEntity(
        userId = null,
        userName = "siqi_wang",
        userDetail = JsonObject("{\"email\": \"wsq.wsq@outlook.com\"}"),
        friendList = JsonArray(),
        groupList = JsonArray(),
        registerTime = OffsetDateTime.now(),
        telephone = "18012350357",
        passWord = "W@ngsq200478"
    )).onSuccess{
        println("Inserted")
    }.onFailure {
        println(it.message)
    }.andThen{
//        userdao.getElementByKey(pool,1).onSuccess{
//            println(it.toString())
//        }.onFailure {
//            println(it.message)
//        }

        userdao.getAllElements(pool).onSuccess{
            if (it != null) {
                for (x in it){
                    println(x.value.toString())
                }
            }
        }.onFailure {
            println(it.message)
        }

//        userdao.getElementsByConditions(pool,"username = $1","siqi_wang").onSuccess{
//            println(it.toString())
//        }.onFailure {
//            println(it.message)
//        }
    }.andThen{
        userdao.deleteElementByKey(pool,3).onSuccess{
            println("Deleted")
        }.onFailure {
            println(it.message)
        }.andThen{
            userdao.getAllElements(pool).onSuccess{
                if (it != null) {
                    for (x in it){
                        println(x.value.toString())
                    }
                }
            }.onFailure {
                println(it.message)
            }
        }
    }




    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
//    println("Program arguments: ${args.joinToString()}")
}