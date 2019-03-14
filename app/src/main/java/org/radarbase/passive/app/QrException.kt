package org.radarbase.passive.app

class QrException : IllegalArgumentException {
    constructor(message: String) : super(message)
    constructor(message: String, ex: Throwable) : super(message, ex)
}
