package verticle

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.*
import io.vertx.kotlin.core.http.*
import io.vertx.kotlin.core.json.*
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.*
import services.Chat
import services.User

class MainVerticle : CoroutineVerticle() {
    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun start() {
        val server = vertx.createHttpServer()

        val mainRouter =  Router.router(vertx)

        mainRouter.route().handler { ctx->
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE,"application/json")

            ctx.next()
        }

        mainRouter.post("/api/hello").handler {
            it.response().end("Hello World!")
        }

        mainRouter.post("/api/user").handler(User.addUser)
        mainRouter.post("/api/token").handler(User.login)

        server.webSocketHandler(Chat.wsHandler)

        server.requestHandler(mainRouter)
        server.listen(8088,"0.0.0.0")
    }
}