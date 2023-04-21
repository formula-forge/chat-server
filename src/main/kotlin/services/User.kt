package services

import dao.ConnectionPool
import dao.UserDao
import dao.entities.UserEntity
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.*
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt
import utilities.AuthUtility

import utilities.CheckUtility
import utilities.ServerUtility.responseError
import utilities.ServerUtility.responseSuccess
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 用户服务类
object User {
    val userdao = UserDao()
    //新增用户 Handler
    @OptIn(DelicateCoroutinesApi::class)
    val addUser = fun(routingContext: RoutingContext){
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

    @OptIn(DelicateCoroutinesApi::class)
    val login = fun(routingContext: RoutingContext){
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
                                        "userName" to user.userName,
                                        "expire" to LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.ofHours(8))
                                    )
                                }
                            )
                            routingContext.response().putHeader(HttpHeaders.SET_COOKIE,"token=${token}")
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

}