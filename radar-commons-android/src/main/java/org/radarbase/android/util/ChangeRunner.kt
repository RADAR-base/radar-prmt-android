package org.radarbase.android.util

class ChangeRunner<T: Any>(initialValue: T? = null, comparator: T.(T?) -> Boolean = Any::equals) : ChangeApplier<T, T>(initialValue, { it }, comparator)
