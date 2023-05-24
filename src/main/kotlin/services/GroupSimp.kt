package services

import dao.ConnectionPool
import dao.GroupDao
import dao.GroupMemberDao
import dao.UserDao
import dao.entities.GroupEntity
import dao.entities.GroupMemberEntity
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


object GroupSimp {

    private val groupDao = GroupDao()
    private val userDao = UserDao()
    private val groupMemberDao = GroupMemberDao()

    private suspend fun isOwner(groupId: Int, me: Int): Boolean {
        val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId)
        return group != null && group.owner == me
    }

    // 获取群组列表
    @OptIn(DelicateCoroutinesApi::class)
    val listGroup = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "listGroup")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                val me = AuthUtility.getUserId(routingContext)

                //获取群组列表
                val groups = groupMemberDao.getGroups(ConnectionPool.getPool(), me, groupDao)

                ServerUtility.responseSuccess(
                    routingContext, 200, json {
                        obj("size" to groups.size, "entries" to groups.map { group ->
                            json {
                                obj(
                                    "role" to group.second,
                                    "name" to group.first.name,
                                    "groupId" to group.first.groupId,
                                    "avatar" to group.first.avatar
                                )
                            }
                        })
                    }, logger
                )

            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                logger.warn(e.message, e)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                logger.warn(e.message, e)
            }
        }
    }

    // 获取群
    @OptIn(DelicateCoroutinesApi::class)
    val getGroup = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getGroup")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId, logger = logger)) return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null) {
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在", logger)
                    return@launch
                }

                val membership = groupMemberDao.getGroupMember(ConnectionPool.getPool(), groupId, me)

                ServerUtility.responseSuccess(
                    routingContext, 200, json {
                        obj(
                            "groupId" to group.groupId,
                            "name" to group.name,
                            "avatar" to group.avatar,
                            "owner" to group.owner,
                            "memberCount" to group.memberCount,
                            "role" to if (membership == null) "stranger" else membership.role,
                        )
                    }, logger
                )
            } catch (e: NumberFormatException) {
                ServerUtility.responseError(routingContext, 400, 2, "参数格式错误", logger)
                return@launch
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                logger.warn(e.message, e)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                logger.warn(e.message, e)
            }
        }
    }

    // 创建群组
    @OptIn(DelicateCoroutinesApi::class)
    val createGroup = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "createGroup")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
                    // 验证token
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取请求体
                    val req = buff.toJsonObject()

                    val name = req.getString("name")

                    req.getString("description")
                    val avatar: String? = req.getString("avatar")

                    req.getJsonArray("members")

                    // 创建群组
                    val groupId = groupDao.insertElement(
                        ConnectionPool.getPool(), GroupEntity(
                            name = name, avatar = avatar, owner = me, memberCount = 1
                        )
                    )

                    // 添加群组成员
                    groupMemberDao.addGroupMembers(
                        ConnectionPool.getPool(), groupId, listOf(
                            GroupMemberEntity(
                                userId = me,
                                role = "owner",
                            )
                        )
                    )

                    ServerUtility.responseSuccess(routingContext, 200, json {
                        obj(
                            "groupId" to groupId,
                        )
                    }, logger)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                    logger.warn(e.message, e)
                }
            }
        }
    }

    // 修改群组
    @OptIn(DelicateCoroutinesApi::class)
    val updateGroup = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "updateGroup")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("groupId")?.toInt()

                    if (ServerUtility.checkNull(routingContext, groupId, logger = logger)) return@launch

                    // 获取请求体
                    val req = buff.toJsonObject()

                    val name: String? = req.getString("name")
                    val avatar: String? = req.getString("avatar")

                    // 验证权限
                    if (!isOwner(groupId!!, me)) {
                        ServerUtility.responseError(routingContext, 403, 2, "权限不足", logger)
                        return@launch
                    }

                    // 修改群组
                    groupDao.updateElementByConditions(
                        ConnectionPool.getPool(), "id = \$%d", GroupEntity(
                            name = name, avatar = avatar
                        ), groupId
                    )

                    ServerUtility.responseSuccess(routingContext, 200, logger = logger)
                } catch (e: NullPointerException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数缺失", logger)
                } catch (e: PgException) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                    logger.warn(e.message, e)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                    logger.warn(e.message, e)
                }
            }
        }
    }

    // 删除群组
    @OptIn(DelicateCoroutinesApi::class)
    val deleteGroup = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "deleteGroup")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                // 验证token
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId, logger = logger)) return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null) {
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在", logger)
                    return@launch
                }
                if (group.owner != me) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足", logger)
                    return@launch
                }

                // 删除群组
                groupDao.deleteElementByKey(ConnectionPool.getPool(), groupId)

                ServerUtility.responseSuccess(routingContext, 200, logger = logger)
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数缺失", logger)
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                logger.warn(e.message, e)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                logger.warn(e.message, e)
            }
        }
    }

    // 获取成员列表
    @OptIn(DelicateCoroutinesApi::class)
    val getGroupMembers = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "getGroupMembers")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                AuthUtility.getUserId(routingContext)
                // 获取群组id
                val groupId = routingContext.pathParam("groupId")!!.toInt()

                // 获取群成员
                val members = groupMemberDao.getGroupMembers(ConnectionPool.getPool(), groupId)

                // 获取群成员信息
                val membersAsUser =
                    userDao.getElementByKeys(ConnectionPool.getPool(), members.map { member -> member.userId!! })

                val membersComposed = members.map { groupMember ->
                    val id = groupMember.userId
                    json {
                        obj(
                            "userId" to id,
                            "name" to membersAsUser[id]?.userName,
                            "avatar" to membersAsUser[id]?.avatar,
                        )
                    }
                }

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "members" to membersComposed
                    )
                }, logger)
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数缺失", logger)
                return@launch
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                logger.warn(e.message, e)
                return@launch
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                logger.warn(e.message, e)
                return@launch
            }
        }
    }

    // 添加成员
    @OptIn(DelicateCoroutinesApi::class)
    val addGroupMember = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "addGroupMember")
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                try {
                    AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("groupId")?.toInt()

                    if (ServerUtility.checkNull(routingContext, groupId, logger = logger)) return@launch

                    // 获取请求体
                    val req = buff.toJsonObject()
                    val userId: Int? = req.getInteger("userId")

                    if (ServerUtility.checkNull(routingContext, userId, logger = logger)) return@launch

                    // 添加成员
                    groupMemberDao.addGroupMembers(
                        ConnectionPool.getPool(), groupId!!, listOf(
                            GroupMemberEntity(
                                userId = userId, role = "member"
                            )
                        )
                    )

                    ServerUtility.responseSuccess(routingContext, 201, logger = logger)
                } catch (e: ClassCastException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数类型错误", logger)
                } catch (e: PgException) {
                    if (e.message != null && e.message!!.contains("foreign key constraint")) ServerUtility.responseError(
                        routingContext,
                        404,
                        2,
                        "用户不存在",
                        logger
                    )
                    else if (e.message != null && e.message!!.contains("duplicate key")) ServerUtility.responseError(
                        routingContext,
                        409,
                        3,
                        "成员已存在",
                        logger
                    )
                    else {
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                        logger.warn(e.message, e)
                    }
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                    logger.warn(e.message, e)
                }
            }
        }
    }

    // 删除成员
    @OptIn(DelicateCoroutinesApi::class)
    val delGroupMember = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "delGroupMember")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()
                // 获取用户id
                val userId = routingContext.pathParam("userId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId, userId, logger = logger)) return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null) {
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在", logger)
                    return@launch
                }
                if (userId != me && group.owner != me || group.owner == userId) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足", logger)
                    return@launch
                }

                // 删除成员
                groupMemberDao.removeGroupMember(ConnectionPool.getPool(), groupId, userId!!)

                ServerUtility.responseSuccess(routingContext, 200, logger = logger)
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数缺失", logger)
            } catch (e: ClassCastException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数类型错误", logger)
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误", logger)
                logger.warn(e.message, e)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误", logger)
                logger.warn(e.message, e)
            }
        }
    }

}