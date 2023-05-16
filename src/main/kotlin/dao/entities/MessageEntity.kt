package dao.entities

import java.net.InetAddress
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

data class MessageEntity(
    var sender : Int?= null,
    var receiver : Int?= null,
    var type : String?= null,
    var time : LocalDateTime?= null,
    var group : Boolean?= null,
    var content : String?= null,
)
