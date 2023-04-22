package services

import dao.ConnectionPool
import dao.UserDao
import dao.entities.UserEntity
import io.vertx.core.http.Cookie
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
import utilities.ServerUtility.responseError
import utilities.ServerUtility.responseSuccess
import java.lang.RuntimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

// 用户服务类
object User {
    val userdao = UserDao()
    //新增用户 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val addUser = fun(routingContext: RoutingContext){
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE,"application/json")
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
                    if (!CheckUtility.checkNotNull(verifyCode,phone,name,password)) {
                        responseError(routingContext, 400, 10, "参数不完整")
                        return@launch
                    }
                    if (!CheckUtility.checkVerifyCode(verifyCode,phone)) {
                        responseError(routingContext, 400, 12, "验证码错误")
                        return@launch
                    }
                    if (!CheckUtility.checkSpecialChars(name!!)) {
                        responseError(routingContext, 400, 1, "用户名不合法")
                        return@launch
                    }
                    if (!CheckUtility.checkPassword(password!!)) {
                        responseError(routingContext, 400, 2, "密码不合法")
                        return@launch
                    }
                    else if (detail != null){
                        try {
                            JsonObject(detail)
                        }
                        catch (e : DecodeException){
                            responseError(routingContext,400,1,"用户详情不合法")
                            return@launch
                        }
                    }

                    //创建用户实体
                    val user = UserEntity(
                        userName = name,
                        userDetail = if (detail == null) null else JsonObject(detail),
                        passWord = BCrypt.hashpw(password,BCrypt.gensalt()),
                        phone = phone,
                        avatar = avatar
                    )

                    //尝试插入数据库
                    try {
                        userdao.insertElement(ConnectionPool.getPool(),user)
                    }
                    catch (e : Exception){
                        //如果用户名已存在
                        if (e.message!= null &&
                            e.message!!.contains("duplicate key")){
                            responseError(routingContext,400,13,"用户名已存在")
                            return@launch
                        }
                        //其他错误
                        responseError(routingContext,500,30,"数据库错误")
                        return@launch
                    }

                    //获取新增的用户
                    val created = userdao
                        .getElementsByConditions(ConnectionPool.getPool(),"username = \$1", name)

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
                }
                catch (e : ClassCastException){
                    responseError(routingContext,400,1,"参数不合法")
                }
                catch (e : DecodeException){
                    responseError(routingContext,400,1,"请求不符合格式")
                }
                catch (e : Exception){
                    responseError(routingContext,500,30,"服务器错误" + e.message)
                }
            }
        }

    }

    //获取用户信息 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val getUser = fun(routingContext: RoutingContext){
        GlobalScope.launch {
            try {
                // 从请求中获取用户id
                val rawId = routingContext.pathParam("id")

                var userId : Int? = null

                if(rawId!= null)
                    userId = try {
                        rawId.toInt()
                    }
                    catch (e : NumberFormatException){
                        responseError(routingContext,400,1,"参数不合法")
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

                if (userId == null || me == userId){
                    userId = me
                    self = true
                    friend = true
                }

                val user = userdao.getElementByKey(ConnectionPool.getPool(),userId)

                if (user == null) {
                    responseError(routingContext,404,4,"用户不存在")
                    return@launch
                }

                val data : JsonObject = json {
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
                    data.put("phone",user.phone)
                }
                //如果不是自己
                else {
                    user.friendList?.forEach { fr ->
                        val friend_obj = try {
                            fr as JsonObject
                        } catch (e: ClassCastException) {
                            responseError(routingContext, 500, 1, "好友列表异常")
                            return@launch
                        }
                        if (friend_obj.getInteger("id") == me) {
                            friend = true
                        }
                    }
                }

                //如果是好友
                if (friend) {
                    data.put("detail",user.userDetail)
                }

                //返回成功
                routingContext.response().end(
                    json { obj("status" to 200,"data" to data, "message" to "OK") }.encode()
                )
            }
            catch (e : ClassCastException){
                responseError(routingContext,400,1,"参数不合法")
            }
            catch (e : Exception){
                responseError(routingContext,500,30,"服务器错误" + e.printStackTrace())
            }
        }
    }

    //修改用户信息 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val updUser = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    // 从请求中获取用户id
                    val userId: Int = try {
                        routingContext.pathParam("id").toInt()
                    } catch (e: NumberFormatException) {
                        responseError(routingContext, 400, 1, "参数不合法")
                        return@launch
                    }
                    println("Updating user ${userId}")

                    //验证token
                    val token = routingContext.request().getCookie("token")!!
                    val subject = AuthUtility.verifyToken(token.value)!!
                    val me = subject.getInteger("userId")!!

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

                        if (name != null ) {
                            if (CheckUtility.checkSpecialChars(name))
                                user.userName = name
                            else {
                                responseError(routingContext, 400, 1, "用户名不合法")
                                return@launch
                            }
                        }

                        if (detail != null){
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
                            userdao.updateElementByConditions(ConnectionPool.getPool(), "id=\$%d",user, userId)
                        }
                        catch (e : UnsupportedOperationException){
                            responseError(routingContext,400,6,"无事可做")
                            return@launch
                        }
                        catch (e : Exception){
                            if (e.message!!.contains("duplicate key"))
                                responseError(routingContext,400,5,"用户名已存在")
                            else
                                responseError(routingContext,500,30,"数据库错误" + e.message)
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
                val userId: Int = try {
                    routingContext.pathParam("id").toInt()
                } catch (e: NumberFormatException) {
                    responseError(routingContext, 400, 1, "参数不合法")
                    return@launch
                }

                println("Deleting user ${userId}")

                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                if (userId != me){
                    responseError(routingContext,403,2,"权限不足")
                    return@launch
                }

                //开始删除用户
                try {
                    userdao.deleteElementByKey(ConnectionPool.getPool(),userId)
                }
                catch (e : Exception){
                    responseError(routingContext,500,30,"数据库错误")
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

    //登录 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val login = fun(routingContext: RoutingContext){
        routingContext.response().putHeader("content-type","application/json")
        routingContext.request().bodyHandler { buff->
            GlobalScope.launch {
                try {
                    val req = buff.toJsonObject().map

                    val userName = req.get("username") as String?
                    val phone = req.get("phone") as String?
                    val password = req.get("password") as String?

                    if ((userName == null && phone == null) || password == null) {
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
                            responseError(routingContext,400,20,"用户不存在")
                            return@launch
                        }

                        val user = userRow.values.first()

                        if (BCrypt.checkpw(password,user.passWord)){
                            val token = AuthUtility.generateToken(
                                json {
                                    obj(
                                        "userId" to user.userId,
                                        "expire" to LocalDateTime.now().plusMonths(1).toEpochSecond(ZoneOffset.ofHours(8))
                                    )
                                }
                            )
                            routingContext.response().addCookie(Cookie.cookie("token",token).setHttpOnly(true).setPath("/").setMaxAge(60*60*24*30))

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
                            responseError(routingContext,400,14,"密码错误")
                        }
                    }
                    catch (e : PgException){
                        responseError(routingContext,500,30,"数据库错误")
                    }
                }
                catch (e : DecodeException){
                    responseError(routingContext,400,1,"请求不符合格式")
                }
                catch (e : ClassCastException){
                    responseError(routingContext,400,1,"参数不合法")
                }
                catch (e : Exception){
                    responseError(routingContext,500,30,"服务器错误" + e.message)
                }
            }
        }
    }

    //登出 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val logout = fun(routingContext: RoutingContext){
        routingContext.response().removeCookie("token").setPath("/")
        routingContext.response().end(
            json { obj("status" to 200, "message" to "OK") }.encode()
        )
    }
}