package org.radarbase.android.util

fun String.takeTrimmedIfNotEmpty(): String? = trim { it <= ' ' }
            .takeUnless(String::isEmpty)
