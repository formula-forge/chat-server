package dao.entities

import java.net.InetAddress
import java.time.OffsetDateTime
import java.util.UUID

data class MessageEntity(
    var messageId : UUID?,
    var messageType : String?,
    var messageSource : Int?,
    var messageDestination : Int?,
    var arriveTime : OffsetDateTime?,
    var sourceAddress : InetAddress?,
)
