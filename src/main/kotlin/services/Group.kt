package services

import dao.ConnectionPool
import dao.GroupDao
import dao.GroupMemberDao
import dao.UserDao
import dao.entities.GroupEntity
import dao.entities.GroupMemberEntity
import dao.entities.UserEntity
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.pgclient.PgException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import utilities.AuthUtility
import utilities.ServerUtility

object Group {
    private val groupDao = GroupDao()
    private val userDao = UserDao()
    private val groupMemberDao = GroupMemberDao()

    private suspend fun checkUserRole(groupId: Int, me: Int, Role: String) {
        val condition = "userid = \$%d" + when (Role) {
            "owner" -> "AND role = 'owner'"
            "admin" -> "AND (role = 'owner' OR role = 'admin')"
            "member" -> ""
            else -> throw InvalidRoleException("Invalid role")
        }

        val member = groupMemberDao.getGroupMembers(ConnectionPool.getPool(), groupId, condition, me)
        if (member.isEmpty())
            throw PermissionDeniedException("Permission denied")
    }

    // 获取群组列表
    @OptIn(DelicateCoroutinesApi::class)
    val listGroup = fun(routingContext: RoutingContext) {
        GlobalScope.launch {
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
                                    "nickname" to group.third,
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
        GlobalScope.launch {
            try {
                // 获取群组id
                val groupId = routingContext.pathParam("groupId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId))
                    return@launch

                val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId!!)

                if (group == null) {
                    ServerUtility.responseError(routingContext, 404, 4, "群组不存在")
                    return@launch
                }

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "groupId" to group.groupId,
                        "name" to group.name,
                        "avatar" to group.avatar,
                        "description" to group.description,
                        "owner" to group.owner,
                        "protected" to group.protected,
                        "memberCount" to group.memberCount,
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
            GlobalScope.launch {
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
                            description = description,
                            avatar = avatar,
                            owner = me,
                            protected = false,
                            memberCount = 1
                        )
                    )

                    // 添加群成员
                    try {
                        groupMemberDao.addGroupMembers(ConnectionPool.getPool(), groupId,
                            if (members != null) members.map { member ->
                                GroupMemberEntity(
                                    userId = member as Int,
                                    role = "member",
                                )
                            } + listOf(
                                GroupMemberEntity(
                                    userId = me,
                                    role = "owner"
                                )
                            ) else listOf(
                                GroupMemberEntity(
                                    userId = me,
                                    role = "owner"
                                )
                            )
                        )
                    } catch (e: PgException) {
                        if (e.message != null && e.message!!.contains("foreign key constraint"))
                            ServerUtility.responseError(routingContext, 404, 4, "用户不存在")
                        else
                            ServerUtility.responseError(routingContext, 500, 30, "数据库错误")
                        e.printStackTrace()
                        return@launch
                    }
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
            GlobalScope.launch {
                try {
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("id")!!.toInt()

                    // 获取请求体
                    val req = buff.toJsonObject()

                    val name: String? = req.getString("name")
                    val description: String? = req.getString("description")
                    val avatar: String? = req.getString("avatar")
                    val protected: Boolean? = req.getBoolean("protected")

                    // 验证权限
                    checkUserRole(groupId, me, "admin")

                    // 修改群组
                    groupDao.updateElementByConditions(
                        ConnectionPool.getPool(),
                        "id = \$%d",
                        GroupEntity(
                            name = name,
                            description = description,
                            avatar = avatar,
                            protected = protected
                        )
                    )

                    ServerUtility.responseSuccess(routingContext, 200)
                } catch (e: PermissionDeniedException) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足")
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
        GlobalScope.launch {
            try {
                // 验证token
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("id")!!.toInt()

                // 验证权限
                checkUserRole(groupId, me, "owner")

                // 删除群组
                groupDao.deleteElementByKey(ConnectionPool.getPool(), groupId)
            } catch (e: PermissionDeniedException) {
                ServerUtility.responseError(routingContext, 403, 2, "权限不足")
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
        GlobalScope.launch {
            try {
                val me = AuthUtility.getUserId(routingContext)
                // 获取群组id
                val groupId = routingContext.pathParam("id")!!.toInt()
                // 验证权限
                checkUserRole(groupId, me, "member")

                // 获取群成员
                val members = groupMemberDao.getGroupMembers(ConnectionPool.getPool(), groupId)

                // 获取群成员信息
                val membersAsUser =
                    userDao.getUsersById(ConnectionPool.getPool(), members.map { member -> member.userId!! })

                val membersComposed = members.map { groupMember ->
                    val id = groupMember.userId
                    json {
                        obj(
                            "userId" to id,
                            "name" to membersAsUser[id]?.userName,
                            "avatar" to membersAsUser[id]?.avatar,
                            "nickname" to groupMember.usrnickname
                        )
                    }
                }

                ServerUtility.responseSuccess(routingContext, 200, json {
                    obj(
                        "members" to membersComposed
                    )
                })
            } catch (e: PermissionDeniedException) {
                ServerUtility.responseError(routingContext, 403, 2, "权限不足")
                return@launch
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
            GlobalScope.launch {
                try {
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("id")!!.toInt()

                    // 获取请求体
                    val req = buff.toJsonObject()
                    val userIds: List<Int>? = req.getJsonArray("userIds")?.map { it as Int }

                    if (ServerUtility.checkNull(routingContext, userIds))
                        return@launch

                    // 获取群信息
                    val group = groupDao.getElementByKey(ConnectionPool.getPool(), groupId)

                    if (group == null) {
                        ServerUtility.responseError(routingContext, 404, 4, "群组不存在")
                    }

                    // 验证权限
                    if (group?.protected == true)
                        checkUserRole(groupId, me, "admin")
                    else
                        checkUserRole(groupId, me, "member")

                    // 添加成员
                    groupMemberDao.addGroupMembers(
                        ConnectionPool.getPool(),
                        groupId,
                        userIds!!.map { userId ->
                            GroupMemberEntity(
                                userId = userId
                            )
                        })

                } catch (e: PermissionDeniedException) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足")
                } catch (e: ClassCastException) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数类型错误")
                } catch (e: PgException) {
                    if (e.message != null && e.message!!.contains("foreign key constraint"))
                        ServerUtility.responseError(routingContext, 404, 2, "用户不存在")
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
        GlobalScope.launch {
            try {
                val me = AuthUtility.getUserId(routingContext)

                // 获取群组id
                val groupId = routingContext.pathParam("id")?.toInt()
                // 获取用户id
                val userId = routingContext.pathParam("userId")?.toInt()

                if (ServerUtility.checkNull(routingContext, groupId, userId))
                    return@launch

                // 验证权限
                if (userId == me) {
                    checkUserRole(groupId!!, me, "member")
                } else {
                    checkUserRole(groupId!!, me, "admin")
                }

                // 删除成员
                groupMemberDao.removeGroupMember(ConnectionPool.getPool(), groupId, userId!!)
            } catch (e: PermissionDeniedException) {
                ServerUtility.responseError(routingContext, 403, 2, "权限不足")
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

    // 修改成员信息
    @OptIn(DelicateCoroutinesApi::class)
    val updateGroupMember = fun(routingContext: RoutingContext) {
        routingContext.request().bodyHandler { buff ->
            GlobalScope.launch {
                try {
                    val me = AuthUtility.getUserId(routingContext)

                    // 获取群组id
                    val groupId = routingContext.pathParam("id")?.toInt()
                    // 获取用户id
                    val userId = routingContext.pathParam("userId")?.toInt()

                    if (ServerUtility.checkNull(routingContext, groupId, userId))
                        return@launch

                    // 验证权限
                    if (userId == me) {
                        checkUserRole(groupId!!, me, "member")
                    } else {
                        checkUserRole(groupId!!, me, "admin")
                    }

                    // 获取请求体
                    val req = buff.toJsonObject()
                    val gpnickname: String? = req.getString("groupNickname")
                    val usrnickname: String? = req.getString("userNickname")

                    // 修改成员信息
                    groupMemberDao
                        .updateGroupMember(
                            ConnectionPool.getPool(),
                            groupId,
                            GroupMemberEntity(
                                userId = userId,
                                gpnickname = gpnickname,
                                usrnickname = usrnickname
                            )

                        )
                } catch (e: PermissionDeniedException) {
                    ServerUtility.responseError(routingContext, 403, 2, "权限不足")
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

    // 申请加入群组
    @OptIn(DelicateCoroutinesApi::class)
    val applyGroup = fun(routingContext : RoutingContext){
        
    }
}

private class PermissionDeniedException(message: String?) : Exception(message)

private class InvalidRoleException(message: String?) : Exception(message)