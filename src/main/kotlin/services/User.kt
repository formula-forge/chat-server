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
import io.vertx.ext.web.*
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt
import utilities.AuthUtility

import utilities.CheckUtility
import utilities.MessageUtility
import utilities.ServerUtility
import utilities.ServerUtility.responseError
import utilities.ServerUtility.responseSuccess
import java.lang.RuntimeException
import java.text.ParseException
import java.time.LocalDateTime
import java.time.ZoneOffset

// 用户服务类
object User {
    val userdao = UserDao()
    val frienddao = FriendDao()

    //新增用户 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val addUser = fun(routingContext: RoutingContext) {
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        routingContext.request().handler { buff ->
            GlobalScope.launch {
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
                        responseError(routingContext, 400, 10, "参数不完整")
                        return@launch
                    }
                    if (!MessageUtility.verifyCode(phone!!,verifyCode!!)) {
                        responseError(routingContext, 400, 12, "验证码过期或错误")
                        return@launch
                    }
                    if (!CheckUtility.checkSpecialChars(name!!)) {
                        responseError(routingContext, 400, 1, "用户名不合法")
                        return@launch
                    }
                    if (!CheckUtility.checkPassword(password!!)) {
                        responseError(routingContext, 400, 2, "密码不合法")
                        return@launch
                    } else if (detail != null) {
                        try {
                            JsonObject(detail)
                        } catch (e: DecodeException) {
                            responseError(routingContext, 400, 1, "用户详情不合法")
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
                        userdao.insertElement(ConnectionPool.getPool(), user)
                    } catch (e: Exception) {
                        //如果用户名已存在
                        if (e.message != null &&
                            e.message!!.contains("duplicate key")
                        ) {
                            responseError(routingContext, 400, 13, "用户名已存在")
                            return@launch
                        }
                        //其他错误
                        responseError(routingContext, 500, 30, "数据库错误")
                        return@launch
                    }

                    //获取新增的用户
                    val created = userdao
                        .getElementsByConditions(ConnectionPool.getPool(), "username = \$1", name)

                    //如果用户创建失败
                    if (created.isNullOrEmpty()) {
                        responseError(routingContext, 500, 30, "用户创建失败")
                        return@launch
                    }

                    //返回成功和用户id
                    responseSuccess(
                        routingContext,
                        201,
                        json {
                            obj(
                                "userId" to created.keys.first(),
                            )
                        }
                    )
                } catch (e : NullPointerException){
                    responseError(routingContext, 400, 1, "参数不完整")
                } catch (e: ClassCastException) {
                    responseError(routingContext, 400, 1, "参数不合法")
                } catch (e: DecodeException) {
                    responseError(routingContext, 400, 1, "请求不符合格式")
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误" + e.message)
                }
            }
        }

    }

    //获取用户信息 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val getUser = fun(routingContext: RoutingContext) {
        GlobalScope.launch {
            try {
                // 从请求中获取用户id
                val rawId = routingContext.pathParam("id")

                var userId: Int? = null

                if (rawId != null)
                    userId = try {
                        rawId.toInt()
                    } catch (e: NumberFormatException) {
                        responseError(routingContext, 400, 1, "参数不合法")
                        return@launch
                    }

                println("Getting user ${userId}")

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

                val user = userdao.getElementByKey(ConnectionPool.getPool(), userId)

                if (user == null) {
                    responseError(routingContext, 404, 4, "用户不存在")
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
                        frienddao.checkFriendShip(ConnectionPool.getPool(), me, userId)
                    } catch (e: Exception) {
                        responseError(routingContext, 500, 30, "数据库错误")
                        return@launch
                    }
                    if(friend)
                        data.put("type", "friend")
                }

                //如果是好友
                if (friend) {
                    data.put("detail", user.userDetail)
                }

                if (!self && !friend) {
                    data.put("type", "stranger")
                }

                //返回成功
                routingContext.response().end(
                    json { obj("status" to 200, "data" to data, "message" to "OK") }.encode()
                )
            } catch (e: ClassCastException) {
                responseError(routingContext, 400, 1, "参数不合法")
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误" + e.printStackTrace())
            }
        }
    }

    //修改用户信息 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val updUser = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
//                     从请求中获取用户id
                    var userId: Int? = try {
                        routingContext.pathParam("id")?.toInt()
                    } catch (e: NumberFormatException) {
                        responseError(routingContext, 400, 1, "参数不合法")
                        return@launch
                    }
                    println("Updating user ${userId}")

                    //验证token
                    val token = routingContext.request().getCookie("token")!!
                    val subject = AuthUtility.verifyToken(token.value)!!
                    val me = subject.getInteger("userId")!!

                    if (userId == null)
                        userId = me

                    if (userId != me) {
                        responseError(routingContext, 403, 2, "权限不足")
                        return@launch
                    }

                    //开始修改用户
                    try {
                        val user = UserEntity()

                        val req = try {
                            buff.toJsonObject().map
                        } catch (e: DecodeException) {
                            responseError(routingContext, 400, 1, "请求不符合格式")
                            return@launch
                        }

                        val name = req["name"] as String?
                        val avatar = req["avatar"] as String?
                        val motto = req["motto"] as String?
                        val detail = req["detail"]
                        val protected = req["protected"] as Boolean?

                        if (name != null) {
                            if (CheckUtility.checkSpecialChars(name))
                                user.userName = name
                            else {
                                responseError(routingContext, 400, 1, "用户名不合法")
                                return@launch
                            }
                        }

                        if (detail != null) {
                            user.userDetail = try {
                                JsonObject.mapFrom(detail)
                            } catch (e: Exception) {
                                responseError(routingContext, 400, 1, "用户详情不合法")
                                return@launch
                            }
                        }

                        user.avatar = avatar
                        user.motto = motto
                        user.protected = protected

                        try {
                            userdao.updateElementByConditions(ConnectionPool.getPool(), "id=\$%d", user, userId)
                        } catch (e: UnsupportedOperationException) {
                            responseError(routingContext, 400, 6, "无事可做")
                            return@launch
                        } catch (e: Exception) {
                            if (e.message != null
                                && e.message!!.contains("duplicate key")
                            )
                                responseError(routingContext, 400, 5, "用户名已存在")
                            else
                                responseError(routingContext, 500, 30, "数据库错误" + e.message)
                            return@launch
                        }

                        //返回成功
                        routingContext.response().end(
                            json { obj("status" to 200, "message" to "OK") }.encode()
                        )
                    } catch (e: ClassCastException) {
                        responseError(routingContext, 400, 1, "参数不合法")
                    } catch (e: Exception) {
                        responseError(routingContext, 500, 30, "服务器错误" + e.message)
                    }
                } catch (e: ClassCastException) {
                    responseError(routingContext, 400, 1, "参数不合法")
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误" + e.message)
                }
            }
        }
    }

    //删除用户 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val delUser = fun(routingContext: RoutingContext) {
        GlobalScope.launch {
            try {
                // 从请求中获取用户id
                var userId: Int? = try {
                    routingContext.pathParam("id")?.toInt()
                } catch (e: NumberFormatException) {
                    responseError(routingContext, 400, 1, "参数不合法")
                    return@launch
                }

                println("Deleting user ${userId}")

                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                if(userId == null)
                    userId = me

                if (userId != me) {
                    responseError(routingContext, 403, 2, "权限不足")
                    return@launch
                }

                //开始删除用户
                try {
                    userdao.deleteElementByKey(ConnectionPool.getPool(), userId)
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "数据库错误")
                    return@launch
                }

                routingContext.response().removeCookie("token").setPath("/")
                //返回成功
                routingContext.response().end(
                    json { obj("status" to 200, "message" to "OK") }.encode()
                )

            } catch (e: ClassCastException) {
                responseError(routingContext, 400, 1, "参数不合法")
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误" + e.printStackTrace())
            }
        }
    }

    //获取收藏公式
    @OptIn(DelicateCoroutinesApi::class)
    val getFavFormula = fun(routingContext: RoutingContext) {
        GlobalScope.launch {
            // 验证token
            val token = routingContext.request().getCookie("token")!!
            val subject = AuthUtility.verifyToken(token.value)!!
            val me = subject.getInteger("userId")!!

            // 获取收藏公式
            try {
                val user = userdao.getElementByKey(ConnectionPool.getPool(), me)
                val fav = user!!.favFormula
                ServerUtility.responseSuccess(routingContext, 200, json { obj("formula" to fav) })
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 404, 4, "用户不存在")
                return@launch
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                e.printStackTrace()
                return@launch
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器错误")
                e.printStackTrace()
                return@launch
            }

        }
    }

    //更新收藏公式
    @OptIn(DelicateCoroutinesApi::class)
    val updateFavFormula = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                // 验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                // 获取收藏公式
                try {
                    userdao.updateElementByConditions(
                        ConnectionPool.getPool(),
                        "id=\$%d",
                        UserEntity(favFormula = buff.toJsonObject()),
                        me
                    )
                    ServerUtility.responseSuccess(routingContext, 200)
                } catch (e: NullPointerException) {
                    ServerUtility.responseError(routingContext, 404, 4, "用户不存在")
                    return@launch
                } catch (e: PgException) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器错误")
                    e.printStackTrace()
                    return@launch
                }

            }
        }
    }

    //登录 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val login = fun(routingContext: RoutingContext) {
        routingContext.response().putHeader("content-type", "application/json")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    val req = buff.toJsonObject().map

                    val userName = req.get("username") as String?
                    val phone = req.get("phone") as String?
                    val password = req.get("password") as String?
                    val code : Int? = (req.get("code") as String?)?.toIntOrNull()

                    if ((userName == null && phone == null) || (password == null && code == null) || (phone == null && code != null))  {
                        responseError(routingContext, 400, 10, "参数不完整")
                        return@launch
                    }

                    try {
                        val userRow = userdao
                            .getElementsByConditions(
                                ConnectionPool.getPool(),
                                "username = \$1 or phone = \$2",
                                userName ?: "",
                                phone ?: ""
                            )

                        if (userRow == null) {
                            responseError(routingContext, 400, 20, "用户不存在")
                            return@launch
                        }

                        val user = userRow.values.first()

                        if ((password != null && BCrypt.checkpw(password, user.passWord) || (code != null && phone != null && MessageUtility.verifyCode(phone, code)))) {
                            val token = AuthUtility.generateToken(
                                json {
                                    obj(
                                        "userId" to user.userId,
                                        "expire" to LocalDateTime.now().plusMonths(1)
                                            .toEpochSecond(ZoneOffset.ofHours(8))
                                    )
                                }
                            )
                            routingContext.response().addCookie(
                                Cookie.cookie("token", token).setHttpOnly(false).setPath("/")
                                    .setMaxAge(60 * 60 * 24 * 30).setSameSite(CookieSameSite.NONE).setSecure(true)
                            )

                            responseSuccess(
                                routingContext,
                                201,
                                json {
                                    obj(
                                        "userId" to user.userId,
                                        "token" to token
                                    )
                                }
                            )
                        }
                        else {
                            responseError(routingContext, 400, 14, "密码错误")
                        }
                    } catch (e: PgException) {
                        responseError(routingContext, 500, 30, "数据库错误")
                    }
                } catch (e: DecodeException) {
                    responseError(routingContext, 400, 1, "请求不符合格式")
                } catch (e: ClassCastException) {
                    responseError(routingContext, 400, 1, "参数不合法")
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误" + e.message)
                }
            }
        }
    }

    //登出 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val logout = fun(routingContext: RoutingContext) {
        routingContext.response().removeCookie("token").setPath("/").setSameSite(CookieSameSite.NONE).setSecure(true)
        routingContext.response().end(
            json { obj("status" to 200, "message" to "OK") }.encode()
        )
    }

    //发送验证码 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val getSMS = fun(routingContext: RoutingContext) {
        routingContext.response().putHeader("content-type", "application/json")
        GlobalScope.launch {
            try {
                val phone = routingContext.request().getParam("phone")

                if (phone == null) {
                    responseError(routingContext, 400, 10, "参数不完整")
                    return@launch
                }

                try {
                    MessageUtility.sendCode(phone)
                } catch (e: IllegalArgumentException) {
                    responseError(routingContext, 400, 1, "手机号不合法")
                    return@launch
                } catch (e : TooManyRequestException) {
                    responseError(routingContext, 429, 1, "请求过于频繁，请${e.tryAfter}秒后重试")
                    return@launch
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误")
                    return@launch
                }

                responseSuccess(routingContext, 200)
            } catch (e: ClassCastException) {
                responseError(routingContext, 400, 1, "参数不合法")
            } catch (e: Exception) {
                responseError(routingContext, 500, 30, "服务器错误" + e.message)
            }
        }
    }

    //更新密码 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val updPassword = fun(routingContext : RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    //获取用户id
                    val id = routingContext.pathParam("id")!!.toInt()

                    //获取手机号和验证码
                    val req = buff.toJsonObject()

                    val phone = req.getString("phone")!!
                    val code = req.getInteger("code")!!

                    val password : String = req.getString("password")!!

                    //验证验证码
                    if (!MessageUtility.verifyCode(phone, code)) {
                        responseError(routingContext, 400, 1, "验证码错误")
                        return@launch
                    }

                    //更新密码
                    userdao.updateElementByConditions(ConnectionPool.getPool(), "id = \$%d AND phone = \$%d", UserEntity(passWord = BCrypt.hashpw(password, BCrypt.gensalt())), id, phone)

                    responseSuccess(routingContext, 200)
                } catch (e: NullPointerException) {
                    responseError(routingContext, 400, 1, "参数不完整")
                    return@launch
                } catch (e : ParseException) {
                    responseError(routingContext, 400, 1, "参数不合法")
                } catch (e: PgException) {
                    responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                } catch (e: Exception) {
                    responseError(routingContext, 500, 30, "服务器错误")
                    e.printStackTrace()
                    return@launch
                }

            }
        }
    }
}