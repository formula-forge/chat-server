package verticle

import dao.ConnectionPool
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import org.slf4j.LoggerFactory
import services.*
import utilities.AuthUtility
import utilities.JaxUtility
import utilities.MessageUtility
import utilities.ServerUtility
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.Path


class MainVerticle : CoroutineVerticle() {
    override suspend fun start() {
        val fh = java.util.logging.FileHandler("log.txt")
        val ch = java.util.logging.ConsoleHandler()
        java.util.logging.Logger.getLogger("")
            .addHandler(fh)
//        java.util.logging.Logger.getLogger("")
//            .addHandler(ch)

        val logger = LoggerFactory.getLogger(this::class.java)
        val storeGlobal = configStoreOptionsOf(
            type = "file",
            format = "yaml",
            config = json {
                obj(
                    "path" to "/etc/chat-server/config.yaml"
                )
            },
            optional = true
        )

        val storeLocal = configStoreOptionsOf(
            type = "file",
            format = "yaml",
            config = json {
                obj(
                    "path" to "config.yaml"
                )
            },
            optional = true
        )

        val retrieverOptions = ConfigRetrieverOptions()
            .addStore(storeGlobal)
            .addStore(storeLocal)

        val config = try {
            val configRetriever = ConfigRetriever
                .create(vertx, retrieverOptions)
            configRetriever
                .config
                .await()
        } catch (e: Exception) {
            logger.error("config file not found", e)
            return
        }

        val smsId = config.getJsonObject("sms").getString("accessKeyId", "")
        val smsSecret = config.getJsonObject("sms").getString("accessKeySecret", "")

        logger.info("Setting SMS access key id to $smsId and secret to *${smsSecret.takeLast(4)}")

        MessageUtility.accessKeyId = smsId
        MessageUtility.accessKeySecret = smsSecret

        val db = config.getJsonObject("database")

        val dbOptions = pgConnectOptionsOf(
            port = db.getInteger("port", 5432),
            host = db.getString("host", "127.0.0.1"),
            database = db.getString("database", "postgres"),
            user = db.getString("user", "postgres"),
            password = db.getString("password", "postgres")
        )

        logger.info("Setting database connection to ${dbOptions.toJson()}")

        ConnectionPool.connect(vertx, dbOptions)

        val authAlg = config.getJsonObject("auth")?.getString("algorithm")
        if (!authAlg.isNullOrEmpty()) {
            when (authAlg) {
                "HS" -> {
                    val key = config.getJsonObject("auth")?.getString("key")
                    if (!key.isNullOrEmpty())
                        AuthUtility.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(key.toByteArray())
                    else
                        AuthUtility.key =
                            io.jsonwebtoken.security.Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256)
                }

                else -> {
                    logger.error("Unknown auth algorithm $authAlg")
                }
            }
        }

        logger.info("Setting auth algorithm to $authAlg")

        val jax = config.getJsonObject("jax", JsonObject())

        if (jax.getBoolean("enable", false)){
            val jaxPort = jax.getInteger("port", 2023)
            val jaxHost = jax.getString("host", "127.0.0.1")
            logger.info("Setting JAX to $jaxHost:$jaxPort")
            User.jax = JaxUtility(vertx, jaxPort, jaxHost)
        }

        val server = vertx.createHttpServer()

        val mainRouter = Router.router(vertx)

        Image.fileSystem = vertx.fileSystem()
        Image.path = Path(config.getString("image-path", "/var/images"))

        logger.info("Setting Image path to ${Image.path}")

        Chat.coroutineContext = vertx.dispatcher()

        val cors = config.getBoolean("cors", true)

        User.cors = cors

        logger.info("Setting CORS to $cors")

        if (cors)
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

        mainRouter.route().order(3).handler { ctx ->
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            val token = ctx.request().getCookie("token")


            val loginLogger = LoggerFactory.getLogger(this.javaClass.name + ".login")

            if (token == null || token.value == "") {
                ServerUtility.responseError(ctx, 401, 0, "未登录或登录过期", loginLogger)
                return@handler
            }

            val subject = try {
                AuthUtility.verifyToken(token.value)
            } catch (e: Exception) {
                ServerUtility.responseError(ctx, 400, 0, "token无效", loginLogger)
                return@handler
            }

            if (subject == null) {
                ServerUtility.responseError(ctx, 400, 0, "token无效", loginLogger)
                return@handler
            }

            val expire = subject.getLong("expire")

            val me = subject.getInteger("userId")

            if (expire == null || me == null) {
                ServerUtility.responseError(ctx, 400, 0, "token无效", loginLogger)
                return@handler
            }

            if (LocalDateTime.now() > LocalDateTime.ofEpochSecond(expire, 0, ZoneOffset.ofHours(8))) {
                ServerUtility.responseError(ctx, 400, 0, "token已过期", loginLogger)
                return@handler
            }

            ctx.next()
        }

        mainRouter.get("/api/user/:id").order(4).handler(User.getUser)
        mainRouter.get("/api/user").order(5).handler(User.getUser)
        mainRouter.delete("/api/user/:id").order(6).handler(User.delUser)
        mainRouter.delete("/api/user").order(6).handler(User.delUser)
        mainRouter.patch("/api/user/:id").order(40).handler(User.updUser)
        mainRouter.patch("/api/user").order(7).handler(User.updUser)
        mainRouter.patch("/api/user/password").order(-15).handler(User.updPassword)

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

        mainRouter.get("/api/connect").order(-20).handler{ ctx->
            if (ctx.request().getHeader("Upgrade") != "websocket")
                ctx.response().setStatusCode(400).end("Not a websocket request")
            else{
                ctx.request().pause()
                ctx.request().toWebSocket().onSuccess(Chat.wsHandler).onFailure{
                    ctx.request().resume()
                }
            }
        }

        Chat.vertx = vertx
        server.webSocketHandler(Chat.wsHandler)

        server.requestHandler(mainRouter)

        val port = config.getInteger("port", 8080)
        val host = config.getString("host", "127.0.0.1")

        logger.info("Server started listening ${host}:${port}")

        val pingTimer = vertx.setPeriodic(10000){
            Chat.ping()
        }

        server.listen(port, host)
    }
}