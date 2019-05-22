package org.radarcns.detail

class QrException : IllegalArgumentException {
    constructor(message: String) : super(message)
    constructor(message: String, ex: Throwable) : super(message, ex)
}
