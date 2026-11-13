package com.shawnkowalchuk.milo.core.io

/**
 * A file or a connection that could not be read or written (ADR-014, phase 3). On Android it is
 * java.io.IOException itself, so the shared code catches exactly what Android's files and
 * DataStore throw; on the iPhone it is a class of Dually's own.
 */
expect open class IOException : Exception {
    constructor(message: String?)

    constructor(message: String?, cause: Throwable?)
}
