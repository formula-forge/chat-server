package verticle

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import services.*
import utilities.AuthUtility
import utilities.ServerUtility
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.Path

class MainVerticle : CoroutineVerticle() {
    override suspend fun start() {
        val server = vertx.createHttpServer()

        val mainRouter =  Router.router(vertx)

        Image.fileSystem = vertx.fileSystem()
        Image.path = Path("/var/images")

        Chat.coroutineContext = vertx.dispatcher()

        mainRouter.route().order(-30).handler(
            CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.PATCH)
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader("Cookie")
                .allowedHeader("X-PINGARUNER")
                .allowCredentials(true)
                .allowedHeader("Access-Control-Allow-Headers")
                .allowedHeader("Authorization")
                .allowedHeader("Access-Control-Allow-Method")
                .allowedHeader("Access-Control-Allow-Origin")
                .allowedHeader("Access-Control-Allow-Credentials")
                .allowedHeader("Content-Type")
        )

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
        mainRouter.delete("/api/user").order(6).handler(User.delUser)
        mainRouter.patch("/api/user/:id").order(7).handler(User.updUser)
        mainRouter.patch("/api/user").order(30).handler(User.updUser)

        mainRouter.delete("/api/token").order(8).handler(User.logout)

        mainRouter.get("/api/friends").order(9).handler(Friend.getFriends)
        mainRouter.post("/api/friends").order(10).handler(Friend.addFriend)
        mainRouter.delete("/api/friends/:id").order(11).handler(Friend.delFriend)
        mainRouter.patch("/api/friends/:id").order(12).handler(Friend.updFriend)
        mainRouter.post("/api/friends/application").order(13).handler(Friend.applyFriend)
        mainRouter.get("/api/friends/application").order(14).handler(Friend.listFriendApp)

        mainRouter.get("/api/session").order(15).handler(Session.getSessionList)
        mainRouter.get("/api/session/user/:id").order(16).handler(Session.getUserMessage)
        mainRouter.get("/api/session/group/:id").order(30).handler(Session.getGroupMessage)
        mainRouter.patch("/api/session/:type/:id").order(17).handler(Session.markSession)
        mainRouter.delete("/api/session/:type/:id").order(18).handler(Session.delMessage)

        mainRouter.get("/api/history").order(19).handler(Session.getHistory)

        mainRouter.get("/api/formula").order(20).handler(User.getFavFormula)
        mainRouter.put("/api/formula").order(21).handler(User.updateFavFormula)

        mainRouter.get("/api/user/sms").order(-2).handler(User.getSMS)

        mainRouter.get("/api/group").order(22).handler(GroupSimp.listGroup)
        mainRouter.get("/api/group/:groupId").order(23).handler(GroupSimp.getGroup)
        mainRouter.patch("/api/group/:groupId").order(24).handler(GroupSimp.updateGroup)
        mainRouter.delete("/api/group/:groupId").order(25).handler(GroupSimp.deleteGroup)
        mainRouter.post("/api/group").order(26).handler(GroupSimp.createGroup)
        mainRouter.post("/api/group/:groupId/member").order(27).handler(GroupSimp.addGroupMember)
        mainRouter.get("/api/group/:groupId/member").order(28).handler(GroupSimp.getGroupMembers)
        mainRouter.delete("/api/group/:groupId/member/:userId").order(29).handler(GroupSimp.delGroupMember)

        mainRouter.post("/api/img").order(-5).handler(Image.upload)
        mainRouter.get("/img/:file").order(-6).handler(Image.download)
        mainRouter.get("/img/avatar/:type/:id").order(-4).handler(Image.avatar)

        Chat.vertx = vertx
        server.webSocketHandler(Chat.wsHandler)

        server.requestHandler(mainRouter)
        server.listen(8080,"0.0.0.0")
    }
}