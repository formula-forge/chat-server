package dao.entities

import java.time.LocalDate

data class FriendAppEntiiy(
    var id : Int? = null,
    var sender : Int? = null,
    var receiver : Int? = null,
    var approved : Boolean? = null,
    var message : String? = null,
    var classification : String? = null,
    var nickname : String? = null,
    var createdAt : LocalDate? = null
)
