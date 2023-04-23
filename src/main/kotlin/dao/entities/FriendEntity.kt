package dao.entities

data class FriendEntity (
    var userId: Int? = null,
    var classification: String? = null,
    var name: String? = null,
    var nickName: String? = null,
    var avatar: String? = null
)