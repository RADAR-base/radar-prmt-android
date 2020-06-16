package org.radarbase.android.util

import java.io.IOException

interface Parser<S, T> {
    @Throws(IOException::class)
    fun parse(value: S): T
}
