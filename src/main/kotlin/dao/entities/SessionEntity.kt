package dao.entities

import java.time.LocalDateTime
import java.time.OffsetDateTime

data class SessionEntity (
    var userId : Int? = null,
    var target : Int? = null,
    var unread : Int? = null,
    var latest : LocalDateTime? = null,
    var latest_msg : String? = null,
    var group : Boolean? = null,
    var hidden : Boolean? = null,
    val expire : LocalDateTime? = null
)