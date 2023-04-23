package services

import dao.ConnectionPool
import dao.FriendDao
import dao.UserDao
import dao.entities.FriendEntity
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import utilities.AuthUtility
import utilities.ServerUtility

object Friend {
    val userdao = UserDao()
    val frienddao = FriendDao()
    //获取好友列表
    @OptIn(DelicateCoroutinesApi::class)
    val getFriends = fun(routingContext : RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    //验证token
                    val token = routingContext.request().getCookie("token")!!
                    val subject = AuthUtility.verifyToken(token.value)!!
                    val me = subject.getInteger("userId")!!

                    //获取好友列表
                    val friends = try {
                        frienddao.listFriends(ConnectionPool.getPool(), me)
                    }
                    catch (e : Exception){
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                        e.printStackTrace()
                        return@launch
                    }

                    //返回
                    ServerUtility.responseSuccess(routingContext, 200, JsonObject().put("friends", friends))
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }
            }
        }
    }

    //添加好友
    @OptIn(DelicateCoroutinesApi::class)
    val addFriend = fun(routingContext : RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    //验证token
                    val token = routingContext.request().getCookie("token")!!
                    val subject = AuthUtility.verifyToken(token.value)!!
                    val me = subject.getInteger("userId")!!

                    //获取参数
                    val req = try {
                        buff.toJsonObject().map
                    }
                    catch (e : DecodeException){
                        ServerUtility.responseError(routingContext, 400, 1, "参数错误")
                        return@launch
                    }

                    //获取好友id
                    val friendId = try {
                        req["userId"] as Int
                    }
                    catch (e: ClassCastException){
                        ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id")
                        return@launch
                    }

                    //获取分组
                    val classification = req["class"] as String?
                    //获取备注
                    val nickName = req["nickname"] as String?

                    //添加好友
                    try {
                        frienddao.addFriend(ConnectionPool.getPool(), me, friendId, classification, nickName)
                    }
                    catch (e : Exception){
                        //好友id不存在
                        if (e.message!= null &&
                            e.message!!.contains("violates foreign key constraint")) {
                            ServerUtility.responseError(routingContext, 404, 4, "好友id不存在")
                            return@launch
                        }
                        //已经是好友
                        if (e.message!= null &&
                            e.message!!.contains("duplicate key")) {
                            ServerUtility.responseError(routingContext, 400, 7, "已经是好友")
                            return@launch
                        }
                        //其他错误
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                        e.printStackTrace()
                        return@launch
                    }

                    //返回
                    ServerUtility.responseSuccess(routingContext, 201, null)
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }
            }
        }
    }

    //删除好友
    @OptIn(DelicateCoroutinesApi::class)
    val delFriend = fun(routingContext : RoutingContext) {
        GlobalScope.launch {
            try {
                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                //获取好友id
                val friendId = try {
                    routingContext.pathParam("id").toInt()
                }
                catch (e: Exception){
                    ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id")
                    return@launch
                }

                //删除好友
                try {
                    frienddao.delFriend(ConnectionPool.getPool(), me, friendId)
                }
                catch (e : Exception){
                     ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                //返回
                ServerUtility.responseSuccess(routingContext, 200, null)
            }
            catch (e : Exception){
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                e.printStackTrace()
                return@launch
            }
        }
    }

    //修改好友信息
    @OptIn(DelicateCoroutinesApi::class)
    val updFriend = fun(routingContext : RoutingContext) {
        routingContext.request().bodyHandler { buff->
            GlobalScope.launch {
                try {
                    //验证token
                    val token = routingContext.request().getCookie("token")!!
                    val subject = AuthUtility.verifyToken(token.value)!!
                    val me = subject.getInteger("userId")!!

                    //获取好友id
                    val friendId = try {
                        routingContext.pathParam("id").toInt()
                    }
                    catch (e: Exception){
                        ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id")
                        return@launch
                    }

                    //获取参数
                    val req = try {
                        buff.toJsonObject().map
                    }
                    catch (e : DecodeException){
                        ServerUtility.responseError(routingContext, 400, 1, "参数错误")
                        return@launch
                    }

                    //获取分组
                    val classification = req["class"] as String?
                    //获取备注
                    val nickName = req["nickname"] as String?

                    //修改好友信息
                    try {
                        frienddao.updateFriend(ConnectionPool.getPool(), me, friendId, classification, nickName)
                    }
                    catch (e : Exception){
                        //其他错误
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                        e.printStackTrace()
                        return@launch
                    }

                    //返回
                    ServerUtility.responseSuccess(routingContext, 200, null)
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }
            }
        }
    }
}