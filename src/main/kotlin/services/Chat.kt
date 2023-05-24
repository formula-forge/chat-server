package services


import dao.*
import dao.entities.MessageEntity
import dao.entities.SessionEntity
import io.vertx.core.Vertx
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utilities.AuthUtility
import utilities.TimeUtility
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Chat {
    private val authedUsers = HashMap<Int, ServerWebSocket>()
    private val messageDao = MessageDao()
    private val sessionDao = SessionDao()
    private val friendDao = FriendDao()
    private val groupMemberDao = GroupMemberDao()
    private val userDao = UserDao()

    var vertx: Vertx? = null

    var coroutineContext: CoroutineContext = EmptyCoroutineContext
    private fun responseError(code: Int, msg: String, socket: ServerWebSocket) {

        try {
            socket.writeTextMessage(json {
                obj(
                    "code" to code,
                    "msg" to msg,
                )
            }.encode())
        } catch (e: Exception) {

        }
    }

    private suspend fun updatePersonalSession(messageEntity: MessageEntity) {
        var session = sessionDao.getElementByKey(
            ConnectionPool.getPool(vertx), messageEntity.sender!!, messageEntity.receiver!!, false
        )
        if (session == null) {
            sessionDao.insertElement(
                ConnectionPool.getPool(vertx), SessionEntity(
                    userId = messageEntity.sender,
                    target = messageEntity.receiver,
                    unread = null,
                    group = false,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content
                )
            )
        } else {
            sessionDao.updateElementByConditions(
                ConnectionPool.getPool(vertx), "id = \$%d AND target = \$%d AND \"group\" = \$%d", SessionEntity(
                    userId = messageEntity.sender,
                    target = messageEntity.receiver,
                    unread = null,
                    group = false,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content,
                    hidden = false
                ), messageEntity.sender!!, messageEntity.receiver!!, false
            )
        }

        session = sessionDao.getElementByKey(
            ConnectionPool.getPool(vertx), messageEntity.receiver!!, messageEntity.sender!!, false
        )
        if (session == null) {
            sessionDao.insertElement(
                ConnectionPool.getPool(vertx), SessionEntity(
                    userId = messageEntity.receiver,
                    target = messageEntity.sender,
                    unread = 1,
                    group = false,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content,
                )
            )
        } else {
            sessionDao.updateElementByConditions(
                ConnectionPool.getPool(vertx), "id = \$%d AND target = \$%d AND \"group\" = \$%d", SessionEntity(
                    userId = messageEntity.receiver,
                    target = messageEntity.sender,
                    unread = session.unread!! + 1,
                    group = null,
                    latest = messageEntity.time,
                    latest_msg = messageEntity.content,
                    hidden = false
                ), messageEntity.receiver!!, messageEntity.sender!!, false
            )
        }
    }

    private suspend fun updateGroupSession(messageEntity: MessageEntity) {
        val groupMemberList = groupMemberDao.getGroupMembers(ConnectionPool.getPool(vertx), messageEntity.receiver!!)

        for (groupMember in groupMemberList) {
            val session = sessionDao.getElementByKey(
                ConnectionPool.getPool(vertx), groupMember.userId!!, messageEntity.receiver!!, true
            )
            if (session == null) {
                sessionDao.insertElement(
                    ConnectionPool.getPool(vertx), SessionEntity(
                        userId = groupMember.userId!!,
                        target = messageEntity.receiver,
                        unread = if (groupMember.userId == messageEntity.sender) null else 1,
                        group = true,
                        latest = messageEntity.time,
                        latest_msg = messageEntity.content,
                    )
                )
            } else {
                sessionDao.updateElementByConditions(
                    ConnectionPool.getPool(vertx), "id = \$%d AND target = \$%d AND \"group\" = \$%d", SessionEntity(
                        userId = groupMember.userId,
                        target = messageEntity.receiver,
                        unread = if (groupMember.userId == messageEntity.sender) null else session.unread!! + 1,
                        group = true,
                        latest = messageEntity.time,
                        latest_msg = messageEntity.content,
                        hidden = false
                    ), groupMember.userId!!, messageEntity.receiver!!, true
                )
            }
        }
    }

    private suspend fun storeMessage(messageEntity: MessageEntity) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".storeMessage")
        try {
            messageDao.insertElement(ConnectionPool.getPool(vertx), messageEntity)
            if (messageEntity.group!!) updateGroupSession(messageEntity)
            else updatePersonalSession(messageEntity)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.warn("Database Error " + e.message, e)
            throw Exception("数据库错误")
        }
    }

    private fun writePersonalMessage(socket: ServerWebSocket, messageEntity: MessageEntity) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".writePersonalMessage")
        logger.info("Sending message from ${messageEntity.sender} to user ${messageEntity.receiver}")

        val targetSocket = authedUsers[messageEntity.receiver]
        if (targetSocket == null || targetSocket.isClosed) {
            responseError(202, "目标用户不在线，消息转存", socket)
            return
        }

        try {
            targetSocket.writeTextMessage(json {
                obj("code" to 1, "message" to json {
                    obj(
                        "target" to messageEntity.receiver,
                        "content" to messageEntity.content,
                        "timestamp" to messageEntity.time?.let { TimeUtility.parseTimeStamp(it) },
                        "group" to null,
                        "type" to "text"
                    )
                })
            }.encode())
        } catch (e: Exception) {
            logger.warn("Socket Error " + e.message, e)
        }
    }

    private suspend fun writeGroupMessage(socket: ServerWebSocket, messageEntity: MessageEntity) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".writeGroupMessage")

        logger.info("Sending message from ${messageEntity.sender} to group ${messageEntity.receiver}")

        val groupMemberList = try {
            groupMemberDao.getGroupMembers(ConnectionPool.getPool(vertx), messageEntity.receiver!!)
        } catch (e: Exception) {
            responseError(500, "数据库错误", socket)
            logger.warn("Database Error" + e.message, e)
            return
        }

        val meU = userDao.getElementByKey(ConnectionPool.getPool(vertx), messageEntity.sender!!)


        groupMemberList.forEach { member ->
            if (member.userId == messageEntity.sender) return@forEach
            val targetSocket = authedUsers[member.userId]
            if (targetSocket == null || targetSocket.isClosed) return@forEach
            try {
                targetSocket.writeTextMessage(json {
                    obj("code" to 1, "message" to json {
                        obj(
                            "target" to messageEntity.sender,
                            "content" to messageEntity.content,
                            "timestamp" to messageEntity.time?.let { TimeUtility.parseTimeStamp(it) },
                            "group" to messageEntity.receiver,
                            "type" to "text",
                            "senderName" to meU?.userName
                        )
                    })
                }.encode())
            } catch (e: Exception) {
                logger.warn("SocketError" + e.message, e)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun relayMessage(req: JsonObject, socket: ServerWebSocket, id: Int) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".relayMessage")

        val message = try {
            req.getJsonObject("message")!!
        } catch (e: Exception) {
            responseError(400, "请提供消息体", socket)
            logger.info("User $id from ${socket.remoteAddress()} Sent Message Without Body", e)
            return
        }

        val group: Boolean = message.getBoolean("group") ?: false

        val target: Int? = message.getInteger("target")

        val content: String? = message.getString("content")

        val timestamp: Long? = message.getLong("timestamp")


        if (target == null || content == null || timestamp == null) {
            responseError(400, "请提供完整的消息体", socket)
            logger.info("User $id from ${socket.remoteAddress()} Sent Message With Incomplete Body")
            return
        }

        GlobalScope.launch(vertx?.dispatcher() ?: EmptyCoroutineContext) {

            val privilege = try {
                if (!group) friendDao.checkFriendShip(ConnectionPool.getPool(vertx), id, target)
                else groupMemberDao.getGroupMember(ConnectionPool.getPool(vertx), target, id) != null
            } catch (e: Exception) {
                responseError(500, "服务器错误", socket)
                logger.warn("Internal Server Error" + e.message, e)
                return@launch
            }

            if (!privilege) {
                responseError(403, "对方不是你的好友或不再群内", socket)
                logger.info("User $id from ${socket.remoteAddress()} Sent Message To Stranger")
                return@launch
            }

            val messageEntity = MessageEntity(
                sender = id,
                receiver = target,
                group = group,
                type = "text",
                time = TimeUtility.parseTime(timestamp),
                content = content,
            )

            try {
                storeMessage(messageEntity)
            } catch (e: Exception) {
                responseError(500, "服务器错误", socket)
                logger.warn("Internal Server Error" + e.message, e)
            }

            if (group) writeGroupMessage(socket, messageEntity)
            else writePersonalMessage(socket, messageEntity)

            socket.writeTextMessage(json {
                obj(
                    "code" to 200, "msg" to "Acknowledged"
                )
            }.encode())

        }
    }

    private fun userAuth(req: JsonObject, socket: ServerWebSocket): Int? {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".userAuth")

        logger.info("Authenticating User From ${socket.remoteAddress()}")

        val token = try {
            req.getString("token")
        } catch (e: Exception) {
            responseError(400, "缺少参数token", socket)
            logger.info("Auth Failed Since No Token is Provided with User from ${socket.remoteAddress()}")
            return null
        }
        val subject = AuthUtility.verifyToken(token)
        if (subject == null) {
            responseError(400, "token无效", socket)
            logger.info(
                "Auth Failed Since Invalid Token is Provided with User from ${socket.remoteAddress()}"
            )
            return null
        }
        val expire = subject.getLong("expire")
        if (expire == null) {
            responseError(400, "token无效", socket)
            logger.info(
                "Auth Failed Since Token Without Timestamp is Provided with User from ${socket.remoteAddress()}"
            )
            return null
        }

        if (LocalDateTime.now() > LocalDateTime.ofEpochSecond(expire, 0, ZoneOffset.ofHours(8))) {
            responseError(400, "token已过期", socket)
            logger.info(
                "Auth Failed Since Expired Token is Provided with User from ${socket.remoteAddress()}"
            )
            return null
        }
        val userId = subject.getInteger("userId")
        if (userId == null) {
            responseError(400, "token无效", socket)
            logger.info(
                "Auth Failed Since Token Without UserId is Provided with User from ${socket.remoteAddress()}"
            )
            return null
        }

        authedUsers[userId] = socket
        socket.writeTextMessage(json {
            obj(
                "code" to 200,
                "msg" to "登录成功",
            )
        }.encode())
        logger.info("Auth Succeeded with User $userId from ${socket.remoteAddress()}")
        return userId
    }

    val wsHandler = fun(socket: ServerWebSocket) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + ".webSocketHandler")
        var id: Int? = null
        socket.handler { buffer ->
            val req = try {
                JsonObject(buffer)
            } catch (e: DecodeException) {
                socket.writeTextMessage(json {
                    obj(
                        "code" to 400, "msg" to "不支持的消息格式"
                    )
                }.encode())
                logger.info("Unsupported Message From ${socket.remoteAddress()}")
                return@handler
            }

            val code = try {
                req.getInteger("code")
            } catch (e: ClassCastException) {
                socket.writeTextMessage(json {
                    obj(
                        "code" to 400, "msg" to "不支持的消息类型"
                    )
                }.encode())
                logger.info("Unsupported Message From ${socket.remoteAddress()}")
            }

            when (code) {
                0 -> {
                    id = try {
                        userAuth(req, socket)
                    } catch (e: Exception) {
                        responseError(500, "服务器内部错误" + e.message, socket)
                        logger.warn("Internal Server Error" + e.message, e)
                        return@handler
                    }
                }

                1 -> {
                    if (id == null) {
                        responseError(400, "请先验证身份", socket)
                        logger.info(
                            "Unauthenticated user from ${socket.remoteAddress()} tried to send message"
                        )
                    } else {
                        try {
                            relayMessage(req, socket, id!!)
                        } catch (e: Exception) {
                            responseError(500, "服务器内部错误" + e.message, socket)
                            logger.warn("Internal Server Error" + e.message, e)
                        }
                    }
                }

                else -> {
                    socket.writeTextMessage(json {
                        obj(
                            "code" to 400, "msg" to "暂不支持此code"
                        )
                    }.encode())
                    logger.info("Unsupported Message From ${socket.remoteAddress()}")
                }
            }

        }
    }
}