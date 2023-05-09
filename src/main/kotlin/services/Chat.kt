package services

import dao.*
import dao.entities.MessageEntity
import dao.entities.SessionEntity
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import utilities.AuthUtility
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.collections.set

object Chat {
    private val authedUsers = HashMap<Int,ServerWebSocket>()
    private val messageDao = MessageDao()
    private val sessionDao = SessionDao()
    private val friendDao = FriendDao()
    private fun responseError(code : Int, msg : String, socket : ServerWebSocket){
        try {
            socket.writeTextMessage(json {
                obj (
                    "code" to code,
                    "msg" to msg,
                )
            }.encode())
        }
        catch (e : Exception){
            e.printStackTrace()
        }
    }

    private suspend fun storeMessage(messageEntity: MessageEntity){
        try {
            messageDao.insertElement(ConnectionPool.getPool(), messageEntity)
            var session = sessionDao.getElementByKey(ConnectionPool.getPool(), messageEntity.sender!!, messageEntity.receiver!!)
            if(session == null){
                sessionDao.insertElement(ConnectionPool.getPool(), SessionEntity(
                    userId = messageEntity.sender,
                    target = messageEntity.receiver,
                    unread = null,
                    group = null,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content
                )
                )
            }
            else{
                sessionDao.updateElementByConditions(ConnectionPool.getPool(), "id = \$%d AND target = \$%d" ,SessionEntity(
                    userId = messageEntity.sender,
                    target = messageEntity.receiver,
                    unread = null,
                    group = null,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content,
                    hidden = false
                ),
                    messageEntity.sender!!,
                    messageEntity.receiver!!
                )
            }

            session = sessionDao.getElementByKey(ConnectionPool.getPool(), messageEntity.receiver!!, messageEntity.sender!!)
            if(session == null){
                sessionDao.insertElement(ConnectionPool.getPool(), SessionEntity(
                    userId = messageEntity.receiver,
                    target = messageEntity.sender,
                    unread = 1,
                    group = null,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content,
                )
                )
            }
            else{
                sessionDao.updateElementByConditions(ConnectionPool.getPool(), "id = \$%d AND target = \$%d" ,SessionEntity(
                    userId = messageEntity.receiver,
                    target = messageEntity.sender,
                    unread = session.unread!! + 1,
                    group = null,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content,
                    hidden = false
                ),
                    messageEntity.receiver!!,
                    messageEntity.sender!!
                )
            }
        }
        catch (e : Exception){
            e.printStackTrace()
            throw Exception("数据库错误")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun relayMessage(req: JsonObject, socket: ServerWebSocket, id: Int){
        if (!authedUsers.containsValue(socket)){
            responseError(400,"请先验证身份",socket)
            return
        }

        val message = try {
            req.getJsonObject("message")
        }
        catch (e : Exception){
            responseError(400,"请提供消息体",socket)
            return
        }

        val target = try{
            message.getInteger("target")
        }
        catch (e : Exception){
            responseError(400,"请提供消息目标",socket)
            return
        }

        val content = try{
            message.getString("content")
        }
        catch (e : Exception){
            responseError(400,"请提供消息内容",socket)
            return
        }

        val timestamp = try {
            message.getLong("timestamp")
        }
        catch (e : Exception){
            responseError(400,"请提供消息时间戳",socket)
            return
        }

        GlobalScope.launch {
            val isFriend = try {
                friendDao.checkFriendShip(ConnectionPool.getPool(),id,target)
            } catch (e : Exception){
                responseError(500,"服务器错误",socket)
                return@launch
            }

            if (!isFriend){
                responseError(403,"对方不是你的好友",socket)
                return@launch
            }

            try {
                storeMessage(MessageEntity(
                    sender = id,
                    receiver = target,
                    type = "text",
                    time = LocalDateTime.ofEpochSecond(timestamp/1000,(timestamp % 1000).toInt() * 1000000, ZoneOffset.ofHours(8)),
                    content = content
                ))
            }
            catch (e : Exception){
                responseError(500,"服务器错误",socket)
            }

            val targetSocket = authedUsers[target]
            if (targetSocket == null || targetSocket.isClosed){
                responseError(202,"目标用户不在线，消息转存",socket)
                return@launch
            }

            try{
                targetSocket.writeTextMessage(json {
                    obj(
                        "code" to 1,
                        "message" to json{
                            obj(
                                "target" to id,
                                "content" to content,
                                "timestamp" to timestamp,
                                "group" to null,
                                "type" to "text"
                            )
                        }
                    )
                }.encode())
            }
            catch (e : Exception){
                responseError(400,"目标用户不在线",socket)
            }

            socket.writeTextMessage(json {
                obj (
                    "code" to 200,
                    "msg" to "Acknowledged"
                )
            }.encode())

        }
    }

    fun userAuth(req : JsonObject, socket: ServerWebSocket) : Int?{
        val token = try {
            req.getString("token")
        }
        catch (e : Exception) {
            responseError(400,"缺少参数token",socket)
            return null
        }
        val subject = AuthUtility.verifyToken(token)
        if (subject == null){
            responseError(400,"token无效",socket)
            return null
        }
        val expire = subject.getLong("expire")
        if(expire == null){
            responseError(400,"token无效",socket)
            return null
        }

        if (LocalDateTime.now() > LocalDateTime.ofEpochSecond(expire,0, ZoneOffset.ofHours(8))){
            responseError(400,"token已过期",socket)
            return null
        }
        val userId = subject.getInteger("userId")
        if (userId == null){
            responseError(400,"token无效",socket)
            return null
        }

        authedUsers[userId] = socket
        socket.writeTextMessage(json {
            obj (
                "code" to 200,
                "msg" to "登录成功",
            )
        }.encode())
        return userId
    }
    
    val wsHandler = fun (socket : ServerWebSocket){
        var id : Int? = null
        socket.handler { buffer ->
            val req = try {
                JsonObject(buffer)
            }
            catch (e : DecodeException){
                socket.writeTextMessage(json {
                    obj (
                        "code" to 400,
                        "msg" to "不支持的消息格式"
                    )
                }.encode())
                return@handler
            }

            val code = try {
                req.getInteger("code")
            }
            catch (e : ClassCastException){
                socket.writeTextMessage(json {
                    obj (
                        "code" to 400,
                        "msg" to "不支持的消息类型"
                    )
                }.encode())
            }

            when(code){
                0 -> {
                    id = try { userAuth(req, socket) }
                    catch (e : Exception) {
                        responseError(500,"服务器内部错误" + e.message,socket)
                        return@handler
                    }
                }
                1 -> {
                    if(id == null){
                        responseError(400,"请先验证身份",socket)
                    }
                    else{
                        try { relayMessage(req, socket, id!!) }
                        catch (e : Exception) {
                            responseError(500,"服务器内部错误" + e.message,socket)
                        }
                    }
                }
                else -> {
                    socket.writeTextMessage(json {
                        obj (
                            "code" to 400,
                            "msg" to "暂不支持此code"
                        )
                    }.encode())
                }
            }

        }
    }
}