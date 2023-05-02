package dao.entities

data class GroupMemberEntity(
    var userId : Int? = null,
    var role : String? = null,
    var usrnickname : String? = null,
    var gpnickname : String? = null
)
