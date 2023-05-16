package services

import dao.*
import dao.entities.GroupAppEntity
import dao.entities.GroupEntity
import dao.entities.GroupMemberEntity
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import utilities.AuthUtility
import utilities.ServerUtility
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object GroupSimp {
    private const val TYPE_POSTED = "posted"

    private const val TYPE_RECEIVED = "received"

    private val groupDao = GroupDao()
    private val userDao = UserDao()
    private val groupMemberDao = GroupMemberDao()
    private val groupAppDao = GroupAppDao()

    var coroutineContext : CoroutineContext = EmptyCoroutineContext

    private suspend fun isOwner(groupId: Int, me: Int) : Boolean {
        val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId)
        return group != null && group.owner == me
    }

    // 获取群组列表
    @OptIn(DelicateCoroutinesApi::class)
    val listGroup = fun(routingContext: RoutingContext) {
        GlobalScope.launch(context = coroutineContext) {
            try {
                val me = AuthUtility.getUserId(routingContext)

                //获取群组列表
                val groups = groupMemberDao.getGroups(ConnectionPool.getPool(), me, groupDao)

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "size" to groups.size,
                        "entries" to groups.map { group ->
                            json {
                                obj(
                                    "role" to group.second,
                                    "name" to group.first.name,
                                    "groupId" to group.first.groupId,
                                    "avatar" to group.first.avatar
                                )
                            }
                        }
                    )
                })

            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                e.printStackTrace()
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                e.printStackTrace()
            }
        }
    }

    // 获取群
    @OptIn(DelicateCoroutinesApi::class)
    val getGroup = fun(routingContext: RoutingContext) {
        GlobalScope.launch(context = coroutineContext) {
            try {
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId))
                    return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null) {
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在")
                    return@launch
                }

                val membership = groupMemberDao.getGroupMember(ConnectionPool.getPool(), groupId, me)

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "groupId" to group.groupId,
                        "name" to group.name,
                        "avatar" to group.avatar,
                        "owner" to group.owner,
                        "memberCount" to group.memberCount,
                        "role" to if (membership == null) "stranger" else membership.role,
                    )
                }
                )
            } catch (e: NumberFormatException) {
                ServerUtility.responseError(routingContext, 400, 2, "参数格式错误")
                return@launch
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                e.printStackTrace()
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                e.printStackTrace()
            }
        }
    }

    // 创建群组
    @OptIn(DelicateCoroutinesApi::class)
    val createGroup = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(context = coroutineContext){
                try {
                    // 验证token
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取请求体
                    val req = buff.toJsonObject()

                    val name = req.getString("name")

                    val description: String? = req.getString("description")
                    val avatar: String? = req.getString("avatar")

                    val members: JsonArray? = req.getJsonArray("members")

                    // 创建群组
                    val groupId = groupDao.insertElement(
                        ConnectionPool.getPool(), GroupEntity(
                            name = name,
                            avatar = avatar,
                            owner = me,
                            memberCount = 1
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
                    })
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                    e.printStackTrace()
                }
            }
        }
    }

    // 修改群组
    @OptIn(DelicateCoroutinesApi::class)
    val updateGroup = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(context = coroutineContext){
                try {
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("groupId")?.toInt()

                    if (ServerUtility.checkNull(routingContext, groupId))
                        return@launch

                    // 获取请求体
                    val req = buff.toJsonObject()

                    val name: String? = req.getString("name")
                    val avatar: String? = req.getString("avatar")

                    // 验证权限
                    if (!isOwner(groupId!!, me)){
                        ServerUtility.responseError(routingContext, 403, 2, "权限不足")
                        return@launch
                    }

                    // 修改群组
                    groupDao.updateElementByConditions(
                        ConnectionPool.getPool(),
                        "id = \$%d",
                        GroupEntity(
                            name = name,
                            avatar = avatar
                        ),
                        groupId
                    )

                    ServerUtility.responseSuccess(routingContext, 200)
                } catch (e: NullPointerException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数缺失")
                    e.printStackTrace()
                } catch (e: PgException) {
                    ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                    e.printStackTrace()
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                    e.printStackTrace()
                }
            }
        }
    }

    // 删除群组
    @OptIn(DelicateCoroutinesApi::class)
    val deleteGroup = fun(routingContext: RoutingContext) {
        GlobalScope.launch(context = coroutineContext){
            try {
                // 验证token
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId))
                    return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null){
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在")
                    return@launch
                }
                if (group.owner != me) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足")
                    return@launch
                }

                // 删除群组
                groupDao.deleteElementByKey(ConnectionPool.getPool(), groupId)

                ServerUtility.responseSuccess(routingContext, 200)
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数缺失")
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                e.printStackTrace()
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                e.printStackTrace()
            }
        }
    }

    // 获取成员列表
    @OptIn(DelicateCoroutinesApi::class)
    val getGroupMembers = fun(routingContext: RoutingContext) {
        GlobalScope.launch(context = coroutineContext){
            try {
                val me = AuthUtility.getUserId(routingContext)
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
                })
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数缺失")
                return@launch
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                e.printStackTrace()
                return@launch
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                e.printStackTrace()
                return@launch
            }
        }
    }

    // 添加成员
    @OptIn(DelicateCoroutinesApi::class)
    val addGroupMember = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch(context = coroutineContext){
                try {
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("groupId")?.toInt()

                    if (ServerUtility.checkNull(routingContext, groupId))
                        return@launch

                    // 获取请求体
                    val req = buff.toJsonObject()
                    val userId: Int? = req.getInteger("userId")

                    if (ServerUtility.checkNull(routingContext, userId))
                        return@launch

                    // 添加成员
                    groupMemberDao.addGroupMembers(
                        ConnectionPool.getPool(),
                        groupId!!,
                        listOf(
                            GroupMemberEntity(
                                userId = userId,
                                role = "member"
                            )
                        )
                    )

                    ServerUtility.responseSuccess(routingContext, 201)
                } catch (e: ClassCastException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数类型错误")
                } catch (e: PgException) {
                    if (e.message != null && e.message!!.contains("foreign key constraint"))
                        ServerUtility.responseError(routingContext, 404, 2, "用户不存在")
                    else if (e.message != null && e.message!!.contains("duplicate key"))
                        ServerUtility.responseError(routingContext, 409, 3, "成员已存在")
                    else {
                        ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                    e.printStackTrace()
                }
            }
        }
    }

    // 删除成员
    @OptIn(DelicateCoroutinesApi::class)
    val delGroupMember = fun(routingContext: RoutingContext) {
        GlobalScope.launch(context = coroutineContext){
            try {
                val me = AuthUtility.getUserId(routingContext)
                
                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()
                // 获取用户id
                val userId = routingContext.pathParam("userId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId, userId))
                    return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null){
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在")
                    return@launch
                }
                if (userId != me && group.owner != me || group.owner == userId) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足")
                    return@launch
                }

                // 删除成员
                groupMemberDao.removeGroupMember(ConnectionPool.getPool(), groupId!!, userId!!)

                ServerUtility.responseSuccess(routingContext, 200)
            } catch (e: NullPointerException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数缺失")
            } catch (e: ClassCastException) {
                ServerUtility.responseError(routingContext, 400, 1, "参数类型错误")
            } catch (e: PgException) {
                ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                e.printStackTrace()
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器内部错误")
                e.printStackTrace()
            }
        }
    }

}