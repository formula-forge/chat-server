package verticle

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.DelicateCoroutinesApi
import services.Chat
import services.User
import utilities.AuthUtility
import utilities.ServerUtility
import java.time.LocalDateTime
import java.time.ZoneOffset

class MainVerticle : CoroutineVerticle() {
    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun start() {
        val server = vertx.createHttpServer()

        val mainRouter =  Router.router(vertx)

        mainRouter.post("/api/hello").order(-1).handler {
            it.response().end("Hello World!")
        }

        mainRouter.post("/api/user").order(0).handler(User.addUser)
        mainRouter.post("/api/token").order(1).handler(User.login).produces("application/json")

        mainRouter.route().order(3).handler { ctx->
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE,"application/json")
            val token = ctx.request().getCookie("token")
            if (token == null || token.value == "") {
                ServerUtility.responseError(ctx, 401, 0, "未登录或登录过期")
                return@handler
            }

            val subject = try {
                AuthUtility.verifyToken(token.value)
            }
            catch (e: Exception){
                ServerUtility.responseError(ctx, 400, 0, "token无效")
                return@handler
            }

            if (subject == null){
                ServerUtility.responseError(ctx, 400, 0, "token无效")
                return@handler
            }

            val expire = subject.getLong("expire")

            val me = subject.getInteger("userId")

            if(expire == null || me == null){
                ServerUtility.responseError(ctx, 400, 0, "token无效")
                return@handler
            }

            if (LocalDateTime.now() > LocalDateTime.ofEpochSecond(expire,0, ZoneOffset.ofHours(8))){
                ServerUtility.responseError(ctx, 400, 0, "token已过期")
                return@handler
            }

            ctx.next()
        }

        mainRouter.get("/api/user/:id").order(4).handler(User.getUser)
        mainRouter.get("/api/user").order(5).handler(User.getUser)
        mainRouter.delete("/api/user/:id").order(6).handler(User.delUser)
        mainRouter.patch("/api/user/:id").order(8).handler(User.updUser)

        mainRouter.delete("/api/token").order(7).handler(User.logout)

        server.webSocketHandler(Chat.wsHandler)

        server.requestHandler(mainRouter)
        server.listen(8088,"0.0.0.0")
    }
}