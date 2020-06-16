package org.radarbase.android.util

open class ChangeApplier<T: Any, V: Any>(initialValue: T?, private val applier: (T) -> V, private val comparator: T.(T?) -> Boolean = Any::equals) {
    private var _value: T? = null

    @get:Synchronized
    val value: T
        get() = checkNotNull(_value) { "Value is not initialized yet" }

    @get:Synchronized
    lateinit var lastResult: V
        private set

    constructor(applier: (T) -> V, comparator: T.(T?) -> Boolean = Any::equals) : this(null, applier, comparator)

    init {
        initialValue?.let { value ->
            applier(value).also {
                synchronized(this) {
                    _value = value
                    lastResult = it
                }
            }
        }
    }

    fun applyIfChanged(value: T, block: ((V) -> Unit)? = null): V {
        return if (!isSame(value)) {
            applier(value)
                    .also { result ->
                        synchronized(this) {
                            _value = value
                            lastResult = result
                        }
                        block?.let { it(result) }
                    }
        } else {
            lastResult
        }
    }

    @Synchronized
    fun isSame(value: T): Boolean {
        return value.comparator(_value)
    }
}
