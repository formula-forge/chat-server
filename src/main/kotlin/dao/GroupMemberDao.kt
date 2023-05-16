package dao

import dao.entities.GroupEntity
import dao.entities.GroupMemberEntity
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Tuple
import io.vertx.sqlclient.impl.ArrayTuple

class GroupMemberDao {
    suspend fun getGroupMembers(connection : PgPool, group : Int, condClause : String? = null, vararg prepared : Any): List<GroupMemberEntity> {
        val sql = StringBuilder("SELECT * FROM group_mem WHERE groupid = \$1")

        val args = ArrayList<Any>()

        args.add(group)

        val range = IntRange(2, 2 + prepared.size).toList().toTypedArray()

        if (condClause != null) {
            sql.append(" AND ")
            sql.append(condClause.format(*range))
            args.addAll(prepared.toList())
        }

        val result = connection
            .preparedQuery("SELECT * FROM group_mem WHERE groupid = \$1")
            .execute(
                Tuple.from(args)
            )
            .await()

        val members = ArrayList<GroupMemberEntity>()
        result.forEach {
            members.add(
                GroupMemberEntity(
                    userId = it.getInteger("userid"),
                    role =  it.getString("role"),
                    usrnickname =  it.getString("usrnickname"),
                    gpnickname = it.getString("gpnickname")
                )
            )
        }
        return members
    }

    suspend fun addGroupMembers(connection: PgPool, group : Int, members : List<GroupMemberEntity>){
        val batch = members.map { member->
            Tuple.of(
                group,
                member.userId,
                member.role,
            )
        }

        connection.preparedQuery("INSERT INTO group_mem (groupid, userid, role) VALUES (\$1, \$2, \$3)")
            .executeBatch(
                batch
            )
            .await()
    }

    suspend fun removeGroupMember(connection: PgPool, group : Int, member : Int){
        connection.preparedQuery("DELETE FROM group_mem WHERE groupid = \$1 AND userid = \$2")
            .execute(
                Tuple.of(
                    group,
                    member
                )
            )
            .await()
    }

    suspend fun updateGroupMember(connection: PgPool, group : Int, member : GroupMemberEntity){
        val clause = StringBuilder()

        val prepared = ArrayTuple(8)

        var counter = 1

        if (member.role != null) {
            clause.append("role = \$${counter++}, ")
            prepared.addString(member.role)
        }

        if(member.usrnickname != null){
            clause.append("usrnickname = \$${counter++}, ")
            prepared.addString(member.usrnickname)
        }

        if(member.gpnickname != null){
            clause.append("gpnickname = \$${counter++}, ")
            prepared.addString(member.gpnickname)
        }

        prepared.addInteger(group)
        prepared.addInteger(member.userId!!)

        connection.preparedQuery("UPDATE group_mem SET %s WHERE groupid = \$${counter++} AND userid = \$${counter}".format(clause.substring(0, clause.length - 2)))
            .execute(
                prepared
            )
            .await()
    }

    suspend fun getGroupMember(connection: PgPool, group : Int, member : Int): GroupMemberEntity? {
        val result = connection.preparedQuery("SELECT * FROM group_mem WHERE groupid = \$1 AND userid = \$2")
            .execute(
                Tuple.of(
                    group,
                    member
                )
            ).await()

        if(result.size() == 0){
            return null
        }

        return GroupMemberEntity(
            userId = result.first().getInteger("userid"),
            role = result.first().getString("role"),
            usrnickname = result.first().getString("usrnickname"),
            gpnickname = result.first().getString("gpnickname")
        )
    }

    suspend fun getGroups(connection: PgPool, me : Int, groupDao: GroupDao) : List<Triple<GroupEntity, String, String>> {
        val result = connection.preparedQuery("SELECT groupid, role, gpnickname FROM group_mem WHERE userid = \$1")
            .execute(
                Tuple.of(
                    me
                )
            ).await()

        return result.map { groupIdCol->
            val groupId = groupIdCol.getInteger("groupid")
            val role = groupIdCol.getString("role")
            val gpnickname = groupIdCol.getString("gpnickname")

            Triple(
                groupDao.getElementByKey(connection, groupId)!!,
                role,
                gpnickname
            )
        }
    }
}