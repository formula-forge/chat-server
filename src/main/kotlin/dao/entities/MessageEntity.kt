package dao.entities

import java.net.InetAddress
import java.time.OffsetDateTime
import java.util.UUID

data class MessageEntity(
    var messageId : UUID?= null,
    var messageType : String?= null,
    var messageSource : Int?= null,
    var messageDestination : Int?= null,
    var arriveTime : OffsetDateTime?= null,
    var sourceAddress : InetAddress?= null,
)
