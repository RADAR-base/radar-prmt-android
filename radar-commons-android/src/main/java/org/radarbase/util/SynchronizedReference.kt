package org.radarbase.util

import java.io.IOException
import java.lang.ref.WeakReference

class SynchronizedReference<T>(private val supplier: () -> T) {
    private var ref: WeakReference<T>? = null

    @Synchronized
    @Throws(IOException::class)
    fun get(): T = ref?.get()
                ?: supplier().also { ref = WeakReference(it) }
}
