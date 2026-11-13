package com.shawnkowalchuk.milo.core.sync

actual class Monitor actual constructor() {
    actual fun <T> locked(block: () -> T): T = synchronized(this, block)
}
