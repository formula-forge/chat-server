package services

import dao.ConnectionPool
import dao.MessageDao
import dao.SessionDao
import dao.UserDao
import dao.entities.SessionEntity
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import utilities.AuthUtility
import utilities.ServerUtility
import utilities.TimeUtility

object Session {
    private val sessiondao = SessionDao()
    private val userdao = UserDao()
    private val messagedao = MessageDao()
    @OptIn(DelicateCoroutinesApi::class)
    val getSessionList = fun(routingContext : RoutingContext){
        GlobalScope.launch {
            try {
                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                //获取会话列表
                val sessions = try {
                    sessiondao.getElements(ConnectionPool.getPool(), "id = \$1", me)
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                if (sessions == null){
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "sessions" to JsonArray(),
                            "unread" to 0
                        )
                    })
                    return@launch
                }

                try {
                    val retSessions = JsonArray()
                    var totUnread = 0

                    sessions.forEach { session ->
                        if(session.group!!){
                            //群聊
                        } else{
                            //私聊
                            val user = try {
                                userdao.getElementByKey(ConnectionPool.getPool(), session.target!!)
                            } catch (e : Exception){
                                ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                                e.printStackTrace()
                                return@launch
                            }

                            retSessions.add(
                                json {
                                    obj (
                                        "type" to "user",
                                        "id" to user?.userId,
                                        "latest" to session.latest_msg,
                                        "time" to TimeUtility.parseTimeStamp(session.latest!!),
                                        "unread" to session.unread
                                    )
                                }
                            )

                            totUnread += session.unread!!
                        }
                    }
                    // 返回
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "sessions" to retSessions,
                            "unread" to totUnread
                        )
                    })
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

            }
            catch (e : Exception){
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                e.printStackTrace()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val getUserMessage = fun(routingContext : RoutingContext){
        GlobalScope.launch {
            try {
                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                val target = try {
                    routingContext.pathParam("id")!!.toInt()
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                //获取会话列表
                val messages = try {
                    messagedao.getElements(ConnectionPool.getPool(), "((sender = \$1 AND receiver = \$2) OR (receiver = \$3 AND sender = \$4))", me, target, me, target)
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                val ret_sessions = JsonArray()
                var count = if (messages == null)  0 else messages.size

                val meUser = userdao.getElementByKey(ConnectionPool.getPool(), me)
                val targetUser = userdao.getElementByKey(ConnectionPool.getPool(), target)

                messages?.forEach { message ->
                    ret_sessions.add(
                        json {
                            obj (
                                "sender" to message.sender,
                                "content" to message.content,
                                "timestamp" to TimeUtility.parseTimeStamp(message.time!!),
                                "type" to message.type,
                                "group" to message.group
                            )
                        }
                    )
                }

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "messages" to ret_sessions,
                        "count" to count
                    )
                })

            }
            catch (e : Exception){
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                e.printStackTrace()
            }
        }
    }

    //标记已读
    @OptIn(DelicateCoroutinesApi::class)
    val markSession = fun(routingContext : RoutingContext){
        GlobalScope.launch {
            try {
                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                val type = try {
                    routingContext.pathParam("type")!!
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                if (type != "user" && type != "group"){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误")
                    return@launch
                }

                val target = try {
                    routingContext.pathParam("id")!!.toInt()
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                try {
                    sessiondao.updateElementByConditions(
                        ConnectionPool.getPool(),
                        "id = \$%d AND target = \$%d AND \"group\" = \$%d",
                        SessionEntity(
                            unread = 0
                        ),
                        me,target,type == "group"
                    )
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                ServerUtility.responseSuccess(routingContext, 200)
            }
            catch (e : Exception){
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                e.printStackTrace()
            }
        }
    }

    //删除会话
    @OptIn(DelicateCoroutinesApi::class)
    val delMessage = fun(routingContext : RoutingContext){
        GlobalScope.launch {
            try {
                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                val type = try {
                    routingContext.pathParam("type")!!
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                if (type != "user" && type != "group"){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误")
                    return@launch
                }

                val target = try {
                    routingContext.pathParam("id")!!.toInt()
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }

                try {
                    sessiondao.deleteElementsByConditions(
                        ConnectionPool.getPool(),
                        "id = \$1 AND target = \$2 AND \"group\" = \$3",
                        me,target,type == "group"
                    )
                }
                catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message)
                    e.printStackTrace()
                    return@launch
                }
                ServerUtility.responseSuccess(routingContext, 200)

            }
            catch (e : Exception){
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message)
                e.printStackTrace()
            }
        }
    }
}