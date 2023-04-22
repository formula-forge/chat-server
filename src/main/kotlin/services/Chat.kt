package services

import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import utilities.AuthUtility
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.collections.set

object Chat {
    private val authedUsers = HashMap<Int,ServerWebSocket>()
    private fun responseError(code : Int, msg : String, socket : ServerWebSocket){
        socket.writeTextMessage(json {
            obj (
                "code" to code,
                "msg" to msg,
            )
        }.encode())
    }

    private fun relayMessage(req: JsonObject, socket: ServerWebSocket, id: Int?){
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



        val targetSocket = authedUsers[target]
        if (targetSocket == null || targetSocket.isClosed){
            responseError(400,"目标用户不在线",socket)
            return
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
                    try { relayMessage(req, socket, id) }
                    catch (e : Exception) {
                        responseError(500,"服务器内部错误" + e.message,socket)
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