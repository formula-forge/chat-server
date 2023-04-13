package dao.entities

import java.util.UUID

data class OfflineMessageEntity(
    var messageId : UUID?,
    var messageDestination : Int?
)
