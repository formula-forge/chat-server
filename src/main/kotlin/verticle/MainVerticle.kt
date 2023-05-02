package verticle

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.DelicateCoroutinesApi
import services.Chat
import services.Friend
import services.Session
import services.User
import utilities.AuthUtility
import utilities.ServerUtility
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

class MainVerticle : CoroutineVerticle() {
    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun start() {
        val server = vertx.createHttpServer()

        val mainRouter =  Router.router(vertx)

        mainRouter.route().order(-3).handler(
            CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.PATCH)
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader("Cookie")
                .allowedHeader("X-PINGARUNER")
                .allowedHeader("Content-Type")
                .allowCredentials(true)
                .allowedHeader("Access-Control-Allow-Origin")
        )

        mainRouter.get("/img/default.png").order(-2).handler{
            val resource = this.javaClass.getResourceAsStream("/img/default.png").buffered()
            it.response().putHeader(HttpHeaders.CONTENT_TYPE,"image/png")
            it.response().end(
                Buffer.buffer(resource.readBytes())
            )
        }

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
        mainRouter.patch("/api/user/:id").order(7).handler(User.updUser)

        mainRouter.delete("/api/token").order(8).handler(User.logout)

        mainRouter.get("/api/friends").order(9).handler(Friend.getFriends)
        mainRouter.post("/api/friends").order(10).handler(Friend.addFriend)
        mainRouter.delete("/api/friends/:id").order(11).handler(Friend.delFriend)
        mainRouter.patch("/api/friends/:id").order(12).handler(Friend.updFriend)
        mainRouter.post("/api/friends/application").order(13).handler(Friend.applyFriend)
        mainRouter.get("/api/friends/application").order(14).handler(Friend.listFriendApp)

        mainRouter.get("/api/session").order(15).handler(Session.getSessionList)
        mainRouter.get("/api/session/user/:id").order(16).handler(Session.getUserMessage)
        mainRouter.patch("/api/session/:type/:id").order(17).handler(Session.markSession)
        mainRouter.delete("/api/session/:type/:id").order(18).handler(Session.delMessage)

        mainRouter.get("/api/history").order(19).handler(Session.getHistory)

        mainRouter.get("/api/formula").order(20).handler(User.getFavFormula)
        mainRouter.put("/api/formula").order(21).handler(User.updateFavFormula)

        mainRouter.get("/api/sms").order(0).handler(User.getSMS)

        server.webSocketHandler(Chat.wsHandler)

        server.requestHandler(mainRouter)
        server.listen(8080,"0.0.0.0")
    }
}