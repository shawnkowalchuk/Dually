package com.shawnkowalchuk.milo.core.sync

import platform.Foundation.NSRecursiveLock

actual class Monitor actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
