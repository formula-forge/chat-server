package services

import dao.ConnectionPool
import dao.FriendAppDao
import dao.FriendDao
import dao.UserDao
import dao.entities.FriendAppEntiiy
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utilities.AuthUtility
import utilities.ServerUtility
import java.time.LocalDate


object Friend {
    private const val TYPE_POSTED = "posted"

    private const val TYPE_RECEIVED = "received"

    private val userDao = UserDao()
    private val friendDao = FriendDao()
    private val friendAppDao = FriendAppDao()

    //获取好友列表
    @OptIn(DelicateCoroutinesApi::class)
    val getFriends = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getFriends")
        GlobalScope.launch {
            try {
                //验证token
                val me = AuthUtility.getUserId(routingContext)

                val classification = routingContext.request().getParam("class")

                //获取好友列表
                var friends = try {
                    friendDao.listFriends(ConnectionPool.getPool(routingContext.vertx()), me)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                if (classification != null) {
                    friends = friends.filter { friend -> friend.classification == classification }
                }

                //返回
                ServerUtility.responseSuccess(
                    routingContext, 200, JsonObject().put("entries", friends).put("size", friends.size), logger
                )
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
                return@launch
            }
        }
    }

    //发送好友申请
    @OptIn(DelicateCoroutinesApi::class)
    val applyFriend = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "applyFriend")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    //验证token
                    val me = AuthUtility.getUserId(routingContext)

                    //获取参数
                    val req = try {
                        buff.toJsonObject().map
                    } catch (e: DecodeException) {
                        ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                        return@launch
                    }

                    //获取好友id
                    val friendId = try {
                        req["receiver"] as Int
                    } catch (e: ClassCastException) {
                        ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id", logger)
                        return@launch
                    }

                    //获取分组
                    val classification = req["classification"] as String?

                    //获取备注
                    val nickname = req["nickname"] as String?

                    //获取申请消息
                    val message = req["message"] as String?

                    //发送好友申请
                    try {
                        friendAppDao.insertElement(
                            ConnectionPool.getPool(routingContext.vertx()), FriendAppEntiiy(
                                sender = me,
                                receiver = friendId,
                                classification = classification,
                                nickname = nickname,
                                message = message,
                                createdAt = LocalDate.now()
                            )
                        )

                        ServerUtility.responseSuccess(routingContext, 200, logger = logger)
                    } catch (e: PgException) {
                        if (e.message != null && e.message!!.contains("foreign key constraint")) {
                            ServerUtility.responseError(routingContext, 404, 4, "好友id不存在", logger)
                        } else {
                            ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                            logger.warn(e.message, e)
                        }
                        return@launch
                    }
                } catch (e: ClassCastException) {
                    ServerUtility.responseError(routingContext, 400, 1, "缺少参数", logger)
                    return@launch
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }
            }
        }
    }

    //添加好友
    @OptIn(DelicateCoroutinesApi::class)
    val addFriend = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "addFriend")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    //验证token
                    val me = AuthUtility.getUserId(routingContext)

                    //获取参数
                    val req = try {
                        buff.toJsonObject().map
                    } catch (e: DecodeException) {
                        ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                        return@launch
                    }

                    //获取好友id
                    val friendId = try {
                        req["userId"] as Int
                    } catch (e: ClassCastException) {
                        ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id", logger)
                        return@launch
                    }

                    val friendUser = try {
                        userDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), friendId)
                    } catch (e: Exception) {
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                        logger.warn(e.message, e)
                        return@launch
                    }

                    if (friendUser == null) {
                        ServerUtility.responseError(routingContext, 404, 4, "好友id不存在", logger)
                        return@launch
                    }

                    var reClass: String? = null
                    var reNickname: String? = null

                    val appId: Int? = try {
                        req["application"] as Int?
                    } catch (e: Exception) {
                        null
                    }

                    if (friendUser.protected!! && appId == null) {
                        ServerUtility.responseError(routingContext, 400, 1, "需要提供申请id", logger)
                        return@launch
                    }

                    if (appId != null) {
                        //获取申请
                        val app = try {
                            friendAppDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), appId)!!
                        } catch (e: NullPointerException) {
                            ServerUtility.responseError(routingContext, 404, 4, "申请id不存在", logger)
                            return@launch
                        } catch (e: Exception) {
                            ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                            logger.warn(e.message, e)
                            return@launch
                        }

                        reClass = app.classification
                        reNickname = app.nickname

                        if (app.receiver != me || app.sender != friendId) {
                            ServerUtility.responseError(routingContext, 403, 2, "权限不足", logger)
                            return@launch
                        }

                        //删除申请
                        try {
                            friendAppDao.updateElementByConditions(
                                ConnectionPool.getPool(routingContext.vertx()), "id = \$%d", FriendAppEntiiy(
                                    approved = true
                                ), appId
                            )
                        } catch (e: Exception) {
                            ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                            logger.warn(e.message, e)
                            return@launch
                        }
                    }

                    //获取分组
                    val classification = req["classification"] as String?
                    //获取备注
                    val nickname = req["nickname"] as String?

                    //添加好友
                    try {
                        friendDao.addFriend(
                            ConnectionPool.getPool(routingContext.vertx()),
                            me,
                            friendId,
                            classification,
                            nickname,
                            reClass,
                            reNickname
                        )
                    } catch (e: Exception) {
                        //好友id不存在
                        if (e.message != null && e.message!!.contains("violates foreign key constraint")) {
                            ServerUtility.responseError(routingContext, 404, 4, "好友id不存在", logger)
                            return@launch
                        }
                        //已经是好友
                        if (e.message != null && e.message!!.contains("duplicate key")) {
                            ServerUtility.responseError(routingContext, 400, 7, "已经是好友", logger)
                            return@launch
                        }
                        //其他错误
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                        logger.warn(e.message, e)
                        return@launch
                    }

                    //返回
                    ServerUtility.responseSuccess(routingContext, 201, null, logger)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }
            }
        }
    }

    //获取好友申请列表
    @OptIn(DelicateCoroutinesApi::class)
    val listFriendApp = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "listFriendApp")
        GlobalScope.launch {
            try {
                //获取过期时间
                val expire: Int = try {
                    routingContext.request().getParam("expire")?.toInt() ?: 30
                } catch (e: NumberFormatException) {
                    ServerUtility.responseError(routingContext, 400, 1, "过期时间格式错误", logger)
                    return@launch
                }

                val type: String = routingContext.request().getParam("type") ?: "both"

                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                //获取好友申请列表
                try {
                    val posted = if (type == TYPE_RECEIVED) null else friendAppDao.getElementsByConditions(
                        ConnectionPool.getPool(routingContext.vertx()),
                        "sender = \$1 AND created_at > \$2",
                        me,
                        LocalDate.now().minusDays(expire.toLong())
                    )

                    val received = if (type == TYPE_POSTED) null else friendAppDao.getElementsByConditions(
                        ConnectionPool.getPool(routingContext.vertx()),
                        "receiver = \$1 AND created_at > \$2",
                        me,
                        LocalDate.now().minusDays(expire.toLong())
                    )

                    fun itemMapping(posted: Boolean): (Map.Entry<Int, FriendAppEntiiy>) -> JsonObject {
                        return fun(item: Map.Entry<Int, FriendAppEntiiy>): JsonObject {
                            val result = json {
                                obj(
                                    "appId" to item.value.id,
                                    "message" to item.value.message,
                                    "approved" to item.value.approved,
                                )
                            }
                            if (posted) result.put("receiver", item.value.receiver)
                            else result.put("sender", item.value.sender)
                            return result
                        }
                    }

                    //返回
                    ServerUtility.responseSuccess(
                        routingContext, 200, json {
                            obj(
                                "posted" to posted?.map(itemMapping(true)),
                                "received" to received?.map(itemMapping(false))
                            )
                        }, logger
                    )
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
                return@launch
            }
        }
    }

    //删除好友
    @OptIn(DelicateCoroutinesApi::class)
    val delFriend = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "delFriend")
        GlobalScope.launch {
            try {
                //验证token
                val token = routingContext.request().getCookie("token")!!
                val subject = AuthUtility.verifyToken(token.value)!!
                val me = subject.getInteger("userId")!!

                //获取好友id
                val friendId = try {
                    routingContext.pathParam("id").toInt()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id", logger)
                    return@launch
                }

                //删除好友
                try {
                    friendDao.delFriend(ConnectionPool.getPool(routingContext.vertx()), me, friendId)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }

                //返回
                ServerUtility.responseSuccess(routingContext, 200, null, logger)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                logger.warn(e.message, e)
                return@launch
            }
        }
    }

    //修改好友信息
    @OptIn(DelicateCoroutinesApi::class)
    val updFriend = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "updFriend")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    //验证token
                    val me = AuthUtility.getUserId(routingContext)

                    //获取好友id
                    val friendId = try {
                        routingContext.pathParam("id").toInt()
                    } catch (e: Exception) {
                        ServerUtility.responseError(routingContext, 400, 1, "需要提供好友id", logger)
                        return@launch
                    }

                    //获取参数
                    val req = try {
                        buff.toJsonObject().map
                    } catch (e: DecodeException) {
                        ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                        return@launch
                    }

                    //获取分组
                    val classification = req["class"] as String?
                    //获取备注
                    val nickname = req["nickname"] as String?

                    //修改好友信息
                    try {
                        friendDao.updateFriend(
                            ConnectionPool.getPool(routingContext.vertx()), me, friendId, classification, nickname
                        )
                    } catch (e: Exception) {
                        //其他错误
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误" + e.message, logger)
                        logger.warn(e.message, e)
                        return@launch
                    }

                    //返回
                    ServerUtility.responseSuccess(routingContext, 200, null, logger)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误" + e.message, logger)
                    logger.warn(e.message, e)
                    return@launch
                }
            }
        }
    }
}