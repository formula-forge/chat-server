package dao.entities

import java.util.UUID

data class OfflineMessageEntity(
    var messageId : UUID? = null,
    var messageDestination : Int? = null
)
