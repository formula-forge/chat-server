package utilities

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneOffset

object TimeUtility {
    fun parseTimeStamp(time : LocalDateTime) : Long {
        return time.toEpochSecond(ZoneOffset.of("+8")) * 1000 + time.nano / 1000000
    }

    fun parseTime(timestamp: Long) : LocalDateTime {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, (timestamp % 1000).toInt() * 1000000, ZoneOffset.ofHours(8))
    }
}