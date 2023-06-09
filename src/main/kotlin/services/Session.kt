package services

import dao.*
import dao.entities.MessageEntity
import dao.entities.SessionEntity
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utilities.AuthUtility
import utilities.ServerUtility
import utilities.TimeUtility
import java.time.LocalDateTime
import java.time.ZoneOffset


object Session {
    private val sessionDao = SessionDao()
    private val userDao = UserDao()
    private val messageDao = MessageDao()
    private val friendDao = FriendDao()
    private val groupMemberDao = GroupMemberDao()

    //获取会话列表
    @OptIn(DelicateCoroutinesApi::class)
    val getSessionList = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getSessionList")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                //获取会话列表
                val sessions = try {
                    sessionDao.getElements(
                        ConnectionPool.getPool(routingContext.vertx()), "id = \$1 AND hidden = false", me
                    )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                if (sessions == null) {
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "sessions" to JsonArray(), "unread" to 0
                        )
                    }, logger)
                    return@launch
                }

                try {
                    val retSessions = JsonArray()
                    var totUnread = 0

                    val userList = ArrayList<Int>()
                    val groupList = ArrayList<Int>()

                    sessions.forEach { session ->
                        if (session.group!!) {
                            //群聊
                            groupList.add(session.target!!)
                        } else {
                            //私聊
                            userList.add(session.target!!)
                        }
                    }

                    val userAvatarMap =
                        userDao.getUsersAvatars(ConnectionPool.getPool(routingContext.vertx()), userList)
                    val groupMap =
                        GroupDao().getElementByKeys(ConnectionPool.getPool(routingContext.vertx()), groupList)
                    val friendMap = friendDao.getFriends(ConnectionPool.getPool(routingContext.vertx()), me, userList)

                    sessions.forEach { session ->
                        if (session.group!!) {
                            //群聊
                            val group = session.target!!

                            if (groupMemberDao.getGroupMember(
                                    ConnectionPool.getPool(routingContext.vertx()), group, me
                                ) == null
                            ) {
                                //不在群里了
                                sessionDao.deleteElementByKey(
                                    ConnectionPool.getPool(routingContext.vertx()), me, group, true
                                )
                                return@forEach
                            }

                            retSessions.add(json {
                                obj(
                                    "type" to "group",
                                    "id" to session.target,
                                    "latest" to session.latest_msg,
                                    "nickname" to groupMap[group]?.name,
                                    "avatar" to groupMap[group]?.avatar,
                                    "time" to TimeUtility.parseTimeStamp(session.latest!!),
                                    "unread" to session.unread
                                )
                            })
                            totUnread += session.unread!!
                        } else {
                            //私聊
                            val id = session.target!!
                            if (!friendMap.containsKey(id)) {
                                //对方已经不是好友了
                                sessionDao.deleteElementByKey(
                                    ConnectionPool.getPool(routingContext.vertx()), me, id, false
                                )
                                return@forEach
                            }
                            retSessions.add(json {
                                obj(
                                    "type" to "user",
                                    "id" to session.target,
                                    "latest" to session.latest_msg,
                                    "nickname" to friendMap[id]?.nickname,
                                    "avatar" to userAvatarMap[id],
                                    "time" to TimeUtility.parseTimeStamp(session.latest!!),
                                    "unread" to session.unread
                                )
                            })

                            totUnread += session.unread!!
                        }
                    }
                    // 返回
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "sessions" to retSessions, "unread" to totUnread
                        )
                    }, logger)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //获取个人会话消息
    @OptIn(DelicateCoroutinesApi::class)
    val getUserMessage = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getUserMessage")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                val target = try {
                    routingContext.pathParam("id")!!.toInt()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message, logger)
                    return@launch
                }

                if (!friendDao.checkFriendShip(ConnectionPool.getPool(routingContext.vertx()), me, target)) {
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "messages" to JsonArray(), "unread" to 0
                        )
                    }, logger)
                    return@launch
                }

                val session = try {
                    sessionDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), me, target, false)
                        ?: SessionEntity()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                if (session.hidden?:true) {
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "messages" to JsonArray(), "unread" to 0
                        )
                    }, logger)
                    return@launch
                }

                //获取消息列表
                val messages = try {
                    messageDao.getElements(
                            ConnectionPool.getPool(routingContext.vertx()),
                            "((sender = \$1 AND receiver = \$2) OR (receiver = \$3 AND sender = \$4)) AND time > \$5 AND \"group\" = false",
                            me,
                            target,
                            me,
                            target,
                            session.expire!!
                        )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                val retSessions = JsonArray()
                val count = messages?.size ?: 0

                messages?.forEach { message ->
                    retSessions.add(json {
                        obj(
                            "sender" to message.sender,
                            "content" to message.content,
                            "timestamp" to TimeUtility.parseTimeStamp(message.time!!),
                            "type" to message.type,
                            "group" to null,
                        )
                    })
                }

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "messages" to retSessions, "count" to count
                    )
                }, logger)

            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //获取群组会话消息
    @OptIn(DelicateCoroutinesApi::class)
    val getGroupMessage = fun(routingContext: RoutingContext)                                                     {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getGroupMessage")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                val group = try {
                    routingContext.pathParam("id")!!.toInt()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message, logger)
                    return@launch
                }

                if (groupMemberDao.getGroupMember(ConnectionPool.getPool(routingContext.vertx()), group, me) == null) {
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "messages" to JsonArray(), "unread" to 0
                        )
                    }, logger)
                    return@launch
                }

                val session = try {
                    sessionDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), me, group, true)
                        ?: SessionEntity(hidden = true)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                if (session.hidden!!) {
                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "messages" to JsonArray(), "unread" to 0
                        )
                    }, logger)
                    return@launch
                }

                //获取消息列表
                val messages = try {
                    messageDao.getElements(
                            ConnectionPool.getPool(routingContext.vertx()),
                            "receiver = \$1 AND time > \$2 AND \"group\" = true",
                            group,
                            session.expire!!
                        )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                val retSessions = JsonArray()
                val count = messages?.size ?: 0

                messages?.forEach { message ->
                    val senderU = userDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), message.sender!!)
                    retSessions.add(json {
                        obj(
                            "sender" to message.sender,
                            "content" to message.content,
                            "timestamp" to TimeUtility.parseTimeStamp(message.time!!),
                            "type" to message.type,
                            "group" to message.receiver,
                            "senderName" to senderU?.userName
                        )
                    })
                }

                ServerUtility.responseSuccess(
                    routingContext, 200, json { obj("messages" to retSessions, "count" to count) }, logger
                )
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                logger.warn(e.message, e)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //标记已读
    @OptIn(DelicateCoroutinesApi::class)
    val markSession = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "markSession")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                val type = try {
                    routingContext.pathParam("type")!!
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message, logger)
                    return@launch
                }

                if (type != "user" && type != "group") {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }

                val target = try {
                    routingContext.pathParam("id")!!.toInt()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message, logger)
                    return@launch
                }

                try {
                    sessionDao.updateElementByConditions(
                        ConnectionPool.getPool(routingContext.vertx()),
                        "id = \$%d AND target = \$%d AND \"group\" = \$%d",
                        SessionEntity(
                            unread = 0
                        ),
                        me,
                        target,
                        type == "group"
                    )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                ServerUtility.responseSuccess(routingContext, 200, logger = logger)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //删除会话
    @OptIn(DelicateCoroutinesApi::class)
    val delMessage = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "delMessage")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                val type = try {
                    routingContext.pathParam("type")!!
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message, logger)
                    return@launch
                }

                if (type != "user" && type != "group") {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }

                val target = try {
                    routingContext.pathParam("id")!!.toInt()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误" + e.message, logger)
                    return@launch
                }

                try {
                    sessionDao.updateElementByConditions(
                        ConnectionPool.getPool(routingContext.vertx()),
                        "id = \$%d AND target = \$%d AND \"group\" = \$%d",
                        SessionEntity(
                            unread = 0, hidden = true, expire = LocalDateTime.now(ZoneOffset.ofHours(8))
                        ),
                        me,
                        target,
                        type == "group"
                    )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }
                ServerUtility.responseSuccess(routingContext, 200, logger = logger)

            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }

    //历史消息
    @OptIn(DelicateCoroutinesApi::class)
    val getHistory = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getHistory")
        GlobalScope.launch {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                //获取参数
                val target: Int? = try {
                    routingContext.request().getParam("id")?.toInt()
                } catch (e: NumberFormatException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }

                val group: Int? = try {
                    routingContext.request().getParam("group")?.toInt()
                } catch (e: NumberFormatException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }

                val begin: LocalDateTime = try {
                    TimeUtility.parseTime(routingContext.request().getParam("begin")!!.toLong())
                } catch (e: Exception) {
                    LocalDateTime.now(ZoneOffset.ofHours(8)).minusDays(30)
                }
                val end: LocalDateTime = try {
                    TimeUtility.parseTime(routingContext.request().getParam("end")!!.toLong())
                } catch (e: Exception) {
                    LocalDateTime.now(ZoneOffset.ofHours(8))
                }

                val offset: Int = try {
                    routingContext.request().getParam("offset", "0").toInt()
                } catch (e: NumberFormatException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }

                val keywords = routingContext.request().getParam("keywords")?.split("+")

                val messages = try {
                    val sql = StringBuilder()
                    val params = ArrayList<Any>(if (keywords == null) 10 else keywords.size + 10)

                    var count = 1

                    if (group == null) {
                        sql.append("\"group\" is null ")
                    } else {
                        sql.append("\"group\" = \$${count++} ")
                        params.add(group)
                    }

                    if (target != null) {
                        sql.append("AND ((sender = \$${count++} AND receiver = \$${count++}) OR (sender = \$${count++} AND receiver = \$${count++})) ")
                        params.add(target)
                        params.add(me)
                        params.add(me)
                        params.add(target)
                    } else {
                        sql.append("AND ( sender = \$${count++} OR receiver = \$${count++} ) ")
                        params.add(me)
                        params.add(me)
                    }

                    sql.append("AND (time BETWEEN \$${count++} AND \$${count++}) ")

                    params.add(begin)
                    params.add(end)

                    if (keywords != null) {
                        for (keyword in keywords) {
                            sql.append("AND content like \$${count++} ")
                            params.add("%${keyword}%")
                        }
                    }

                    sql.append("ORDER BY time DESC LIMIT 100 OFFSET \$${count}")
                    params.add(offset)

                    print(sql.toString())
                    print(params)

                    messageDao.getElements(
                        ConnectionPool.getPool(routingContext.vertx()), sql.toString(), *params.toArray()
                    )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                val mapper = fun(message: MessageEntity): JsonObject {
                    return json {
                        obj(
                            "sender" to message.sender,
                            "receiver" to message.receiver,
                            "content" to message.content,
                            "timestamp" to TimeUtility.parseTimeStamp(message.time!!),
                            "type" to message.type,
                            "group" to message.group
                        )
                    }
                }

                ServerUtility.responseSuccess(
                    routingContext, 200, json {
                        obj(
                            "messages" to messages?.map(mapper), "count" to messages?.size
                        )
                    }, logger = logger
                )

            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
            }
        }
    }
}
