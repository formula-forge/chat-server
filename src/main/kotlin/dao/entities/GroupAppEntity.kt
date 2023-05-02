package dao.entities

import java.time.LocalDate

data class GroupAppEntity(
    var id : Int? = null,
    var sender : Int? = null,
    var group : Int? = null,
    var approved : Boolean? = null,
    var message : String? = null,
    var createdAt : LocalDate? = null
)
