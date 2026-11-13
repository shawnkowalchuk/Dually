package com.shawnkowalchuk.milo.core.sync

/**
 * A lock for state that more than one thread reads and writes (ADR-014, phase 3). On Android it
 * is Java's own `synchronized` on this object, so the code that used `synchronized(lock)` before
 * the move locks exactly as it did; on the iPhone it is Apple's recursive lock. Like Java's, it
 * may be taken again by the thread that holds it.
 */
expect class Monitor() {
    /** Runs [block] while holding the lock, and gives back what it gives. */
    fun <T> locked(block: () -> T): T
}
