package dao.entities

data class FriendEntity (
    var userId: Int? = null,
    var classification: String? = null,
    var name: String? = null,
    var nickname: String? = null,
    var avatar: String? = null
)