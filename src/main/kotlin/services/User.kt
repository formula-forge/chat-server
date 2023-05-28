package services

import TooManyRequestException
import dao.ConnectionPool
import dao.FriendDao
import dao.UserDao
import dao.entities.UserEntity
import io.vertx.core.http.Cookie
import io.vertx.core.http.CookieSameSite
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import utilities.*
import utilities.ServerUtility.responseError
import utilities.ServerUtility.responseSuccess
import java.text.ParseException
import java.time.LocalDateTime
import java.time.ZoneOffset


// 用户服务类
object User {
    private val userDao = UserDao()
    private val friendDao = FriendDao()

    var cors = true
    var jax : JaxUtility? = null

    //新增用户 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val addUser = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".addUser")
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        routingContext.request().handler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
                    val req = buff.toJsonObject().map

                    //获取请求参数
                    val verifyCode = req["verifyCode"] as Int?
                    val phone = req["phone"] as String?
                    val name = req["name"] as String?
                    val password = req["password"] as String?
                    val detail = req["detail"] as String?
                    val avatar = req["avatar"] as String?

                    //验证参数
                    if (!CheckUtility.checkNotNull(verifyCode, phone, name, password)) {
                        responseError(routingContext, 400, 10, "参数不完整", logger)
                        return@launch
                    }
                    if (!MessageUtility.verifyCode(phone!!, verifyCode!!)) {
                        responseError(routingContext, 400, 12, "验证码过期或错误", logger)
                        return@launch
                    }
                    if (!CheckUtility.checkSpecialChars(name!!)) {
                        responseError(routingContext, 400, 1, "用户名不合法", logger)
                        return@launch
                    }
                    if (!CheckUtility.checkPassword(password!!)) {
                        responseError(routingContext, 400, 2, "密码不合法", logger)
                        return@launch
                    } else if (detail != null) {
                        try {
                            JsonObject(detail)
                        } catch (e: DecodeException) {
                            responseError(routingContext, 400, 1, "用户详情不合法", logger)
                            return@launch
                        }
                    }

                    //创建用户实体
                    val user = UserEntity(
                        userName = name,
                        userDetail = if (detail == null) null else JsonObject(detail),
                        passWord = BCrypt.hashpw(password, BCrypt.gensalt()),
                        phone = phone,
                        avatar = avatar
                    )

                    //尝试插入数据库
                    try {
                        userDao.insertElement(ConnectionPool.getPool(routingContext.vertx()), user)
                    } catch (e: Exception) {
                        //如果用户名已存在
                        if (e.message != null && e.message!!.contains("duplicate key")) {
                            responseError(routingContext, 400, 13, "用户名已存在", logger)
                            return@launch
                        }
                        //其他错误
                        responseError(routingContext, 500, 30, "数据库错误", logger)
                        logger.warn(e.message, e)
                        return@launch
                    }

                    //获取新增的用户
                    val created = userDao.getElementsByConditions(
                            ConnectionPool.getPool(routingContext.vertx()),
                            "username = \$1",
                            name
                        )

                    //如果用户创建失败
                    if (created.isNullOrEmpty()) {
                        responseError(routingContext, 500, 30, "用户创建失败", logger)
                        return@launch
                    }

                    //返回成功和用户id
                    responseSuccess(
                        routingContext, 201, json {
                            obj(
                                "userId" to created.keys.first(),
                            )
                        }, logger
                    )
                } catch (e: NullPointerException) {
                    responseError(routingContext, 400, 1, "参数不完整", logger)
                } catch (e: ClassCastException) {
                    responseError(routingContext, 400, 1, "参数不合法", logger)
                } catch (e: DecodeException) {
                    responseError(routingContext, 400, 1, "请求不符合格式", logger)
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                    logger.warn(e.message, e)
                }
            }
        }

    }

    //获取用户信息 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val getUser = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".getUser")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                // 从请求中获取用户id
                val rawId = routingContext.pathParam("id")

                var userId: Int? = null

                if (rawId != null) userId = try {
                    rawId.toInt()
                } catch (e: NumberFormatException) {
                    responseError(routingContext, 400, 1, "参数不合法", logger)
                    return@launch
                }

                //验证token
                val token = routingContext.request().getCookie("token")!!

                val subject = AuthUtility.verifyToken(token.value)!!

                val me = subject.getInteger("userId")!!

                //开始查询用户

                var self = false //查询自己
                var friend = false //查询好友

                if (userId == null || me == userId) {
                    userId = me
                    self = true
                    friend = true
                }

                val user = userDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), userId)

                if (user == null) {
                    responseError(routingContext, 404, 4, "用户不存在", logger)
                    return@launch
                }

                val data: JsonObject = json {
                    obj(
                        "userId" to user.userId,
                        "name" to user.userName,
                        "avatar" to user.avatar,
                        "motto" to user.motto,
                        "protected" to user.protected
                    )
                }

                //如果是自己
                if (self) {
                    data.put("phone", user.phone)
                    data.put("type", "me")
                }
                //如果不是自己
                else {
                    friend = try {
                        friendDao.checkFriendShip(ConnectionPool.getPool(routingContext.vertx()), me, userId)
                    } catch (e: Exception) {
                        responseError(routingContext, 500, 30, "数据库错误", logger)
                        logger.warn(e.message, e)
                        return@launch
                    }
                    if (friend) data.put("type", "friend")
                }

                //如果是好友
                if (friend) {
                    data.put("detail", user.userDetail)
                }

                if (!self && !friend) {
                    data.put("type", "stranger")
                }

                ServerUtility.responseSuccess(
                    routingContext,
                    200,
                    json { obj("status" to 200, "data" to data, "message" to "OK") },
                    logger
                )
            } catch (e: ClassCastException) {
                responseError(routingContext, 400, 1, "参数不合法", logger)
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //修改用户信息 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val updUser = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".updUser")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
//                     从请求中获取用户id
                    var userId: Int? = try {
                        routingContext.pathParam("id")?.toInt()
                    } catch (e: NumberFormatException) {
                        responseError(routingContext, 400, 1, "参数不合法", logger)
                        return@launch
                    }

                    //验证token
                    val me = AuthUtility.getUserId(routingContext)

                    if (userId == null) userId = me

                    if (userId != me) {
                        responseError(routingContext, 403, 2, "权限不足", logger)
                        return@launch
                    }

                    //开始修改用户
                    try {
                        val user = UserEntity()

                        val req = try {
                            buff.toJsonObject().map
                        } catch (e: DecodeException) {
                            responseError(routingContext, 400, 1, "请求不符合格式", logger)
                            return@launch
                        }

                        val name = req["name"] as String?
                        val avatar = req["avatar"] as String?
                        val motto = req["motto"] as String?
                        val detail = req["detail"]
                        val protected = req["protected"] as Boolean?

                        if (name != null) {
                            if (CheckUtility.checkSpecialChars(name)) user.userName = name
                            else {
                                responseError(routingContext, 400, 1, "用户名不合法", logger)
                                return@launch
                            }
                        }

                        if (detail != null) {
                            user.userDetail = try {
                                JsonObject.mapFrom(detail)
                            } catch (e: Exception) {
                                responseError(routingContext, 400, 1, "用户详情不合法", logger)
                                return@launch
                            }
                        }

                        user.avatar = avatar
                        user.motto = motto
                        user.protected = protected

                        try {
                            userDao.updateElementByConditions(
                                ConnectionPool.getPool(routingContext.vertx()), "id=\$%d", user, userId
                            )
                        } catch (e: UnsupportedOperationException) {
                            responseError(routingContext, 400, 6, "无事可做", logger)
                            return@launch
                        } catch (e: Exception) {
                            if (e.message != null && e.message!!.contains("duplicate key")) responseError(
                                routingContext,
                                400,
                                5,
                                "用户名已存在",
                                logger
                            )
                            else responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                            logger.warn(e.message, e)
                            return@launch
                        }

                        //返回成功
                        responseSuccess(routingContext, 200, logger = logger)
                    } catch (e: ClassCastException) {
                        responseError(routingContext, 400, 1, "参数不合法", logger)
                    } catch (e: Exception) {
                        responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                        logger.warn(e.message, e)
                    }
                } catch (e: ClassCastException) {
                    responseError(routingContext, 400, 1, "参数不合法", logger)
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                    logger.warn(e.message, e)
                }
            }
        }
    }

    //删除用户 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val delUser = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".delUser")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                // 从请求中获取用户id
                var userId: Int? = try {
                    routingContext.pathParam("id")?.toInt()
                } catch (e: NumberFormatException) {
                    responseError(routingContext, 400, 1, "参数不合法", logger)
                    return@launch
                }

                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                if (userId == null) userId = me

                if (userId != me) {
                    responseError(routingContext, 403, 2, "权限不足", logger)
                    return@launch
                }

                //开始删除用户
                try {
                    userDao.deleteElementByKey(ConnectionPool.getPool(routingContext.vertx()), userId)
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "数据库错误", logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                routingContext.response().removeCookie("token").setPath("/")
                //返回成功
                routingContext.response().end(
                    json { obj("status" to 200, "message" to "OK") }.encode()
                )

            } catch (e: ClassCastException) {
                responseError(routingContext, 400, 1, "参数不合法", logger)
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //获取收藏公式
    @OptIn(DelicateCoroutinesApi::class)
    val getFavFormula = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".getFavFormula")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            // 验证token
            val token = routingContext.request().getCookie("token")!!
            val subject = AuthUtility.verifyToken(token.value)!!
            val me = subject.getInteger("userId")!!

            // 获取收藏公式
            try {
                val user = userDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), me)
                val fav = user!!.favFormula
                responseSuccess(routingContext, 200, json { obj("formula" to fav) }, logger = logger)
            } catch (e: NullPointerException) {
                responseError(routingContext, 404, 4, "用户不存在", logger)
                return@launch
            } catch (e: PgException) {
                responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                logger.warn(e.message, e)
                return@launch
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误", logger)
                logger.warn(e.message, e)
                return@launch
            }

        }
    }

    //更新收藏公式
    @OptIn(DelicateCoroutinesApi::class)
    val updateFavFormula = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".updateFavFormula")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                // 验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!
                val svgForula = try {
                    jax?.transformFav(buff.toJsonObject())
                } catch (e : Exception){
                    null
                }
                // 获取收藏公式
                try {
                    userDao.updateElementByConditions(
                        ConnectionPool.getPool(routingContext.vertx()),
                        "id=\$%d",
                        UserEntity(favFormula = svgForula?:buff.toJsonObject()),
                        me
                    )
                    responseSuccess(routingContext, 200, logger = logger)
                } catch (e: NullPointerException) {
                    responseError(routingContext, 404, 4, "用户不存在", logger)
                    return@launch
                } catch (e: PgException) {
                    responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误", logger)
                    logger.warn(e.message, e)
                    return@launch
                }

            }
        }
    }

    //登录 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val login = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".login")
        routingContext.response().putHeader("content-type", "application/json")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
                    val req = buff.toJsonObject().map

                    val userName = req["username"] as String?
                    val phone = req["phone"] as String?
                    val password = req["password"] as String?
                    val code: Int? = (req["code"] as String?)?.toIntOrNull()

                    if ((userName == null && phone == null) || (password == null && code == null) || (phone == null && code != null)) {
                        responseError(routingContext, 400, 10, "参数不完整", logger)
                        return@launch
                    }

                    try {
                        val userRow = userDao.getElementsByConditions(
                                ConnectionPool.getPool(routingContext.vertx()),
                                "username = \$1 or phone = \$2",
                                userName ?: "",
                                phone ?: ""
                            )

                        if (userRow == null) {
                            responseError(routingContext, 400, 20, "用户不存在", logger)
                            return@launch
                        }

                        val user = userRow.values.first()

                        if ((password != null && BCrypt.checkpw(
                                password, user.passWord
                            ) || (code != null && phone != null && MessageUtility.verifyCode(phone, code)))
                        ) {
                            val token = AuthUtility.generateToken(json {
                                obj(
                                    "userId" to user.userId,
                                    "expire" to LocalDateTime.now().plusMonths(1).toEpochSecond(ZoneOffset.ofHours(8))
                                )
                            })
                            routingContext.response().addCookie(
                                if (cors)
                                    Cookie.cookie("token", token).setHttpOnly(false).setPath("/")
                                        .setMaxAge(60 * 60 * 24 * 30).setSameSite(CookieSameSite.NONE).setSecure(true)
                                else
                                    Cookie.cookie("token", token).setHttpOnly(false).setPath("/")
                                        .setMaxAge(60 * 60 * 24 * 30).setSecure(false)
                            )

                            responseSuccess(
                                routingContext, 201, json {
                                    obj(
                                        "userId" to user.userId, "token" to token
                                    )
                                }, logger = logger
                            )
                        } else {
                            responseError(routingContext, 400, 14, "密码错误", logger)
                        }
                    } catch (e: PgException) {
                        responseError(routingContext, 500, 30, "数据库错误", logger)
                        logger.warn(e.message, e)
                    }
                } catch (e: DecodeException) {
                    responseError(routingContext, 400, 1, "请求不符合格式", logger)
                } catch (e: ClassCastException) {
                    responseError(routingContext, 400, 1, "参数不合法", logger)
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                    logger.warn(e.message, e)
                }
            }
        }
    }

    //登出 Handler
    val logout = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".logout")
        if (cors)
            routingContext.response().removeCookie("token").setPath("/").setSameSite(CookieSameSite.NONE).setSecure(true)
        else
            routingContext.response().removeCookie("token").setPath("/").setSecure(false)
        responseSuccess(routingContext, 200, logger = logger)
    }

    //发送验证码 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val getSMS = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".getSMS")
        routingContext.response().putHeader("content-type", "application/json")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                val phone = routingContext.request().getParam("phone")

                if (phone == null) {
                    responseError(routingContext, 400, 10, "参数不完整", logger)
                    return@launch
                }

                try {
                    MessageUtility.sendCode(phone,logger)
                } catch (e: IllegalArgumentException) {
                    responseError(routingContext, 400, 1, "手机号不合法", logger)
                    return@launch
                } catch (e: TooManyRequestException) {
                    responseError(routingContext, 429, 1, "请求过于频繁，请${e.tryAfter}秒后重试", logger)
                    return@launch
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误", logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                responseSuccess(routingContext, 200, logger = logger)
            } catch (e: ClassCastException) {
                responseError(routingContext, 400, 1, "参数不合法", logger)
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //更新密码 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val updPassword = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".updPassword")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
                    routingContext.response().putHeader("content-type", "application/json")
                    //获取手机号和验证码
                    val req = buff.toJsonObject()

                    val phone = req.getString("phone")!!
                    val code = req.getInteger("verifyCode")!!

                    val password: String = req.getString("password")!!

                    //验证验证码
                    if (!MessageUtility.verifyCode(phone, code)) {
                        responseError(routingContext, 400, 1, "验证码错误", logger)
                        return@launch
                    }

                    //更新密码
                    val count = userDao.updateElementByConditions(
                        ConnectionPool.getPool(routingContext.vertx()),
                        "phone = \$%d",
                        UserEntity(passWord = BCrypt.hashpw(password, BCrypt.gensalt())),
                        phone
                    )

                    if (count == 0){
                        responseError(routingContext, 400, 9, "没有更新任何内容", logger)
                        return@launch
                    }

                    responseSuccess(routingContext, 200, logger = logger)
                } catch (e: NullPointerException) {
                    responseError(routingContext, 400, 1, "参数不完整", logger)
                    return@launch
                } catch (e: ParseException) {
                    responseError(routingContext, 400, 1, "参数不合法", logger)
                } catch (e: PgException) {
                    responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误", logger)
                    logger.warn(e.message, e)
                    return@launch
                }

            }
        }
    }
}