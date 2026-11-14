package com.shawnkowalchuk.milo.core.io

/**
 * Text read a buffer at a time (2026-11-13, for the import that both phones share). On Android it
 * is java.io.Reader itself, so the import reads files exactly as it always did; on the iPhone it
 * is a class of Dually's own that reads a UTF-8 file (IosFileReader).
 */
expect abstract class Reader : AutoCloseable {
    /** Fills [buffer] from its start; how many characters it put there, or -1 at the end. */
    open fun read(buffer: CharArray): Int

    abstract override fun close()
}
