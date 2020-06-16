package org.radarbase.android.util

import java.util.Objects.hash

class TimedLong(val value: Long) {
    val time = System.currentTimeMillis()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TimedLong
        return time == other.time && value == other.value
    }

    override fun hashCode(): Int {
        return hash(time, value)
    }
}
