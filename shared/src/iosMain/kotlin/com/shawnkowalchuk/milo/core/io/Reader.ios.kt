package com.shawnkowalchuk.milo.core.io

import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

actual abstract class Reader : AutoCloseable {
    actual open fun read(buffer: CharArray): Int = read(buffer, 0, buffer.size)

    abstract fun read(buffer: CharArray, offset: Int, length: Int): Int

    actual abstract override fun close()
}

/**
 * A UTF-8 file, read a code point at a time into the buffers the import hands it, so a file of
 * any size is never held in memory whole. A character beyond the first plane comes as its two
 * halves, as Java's Reader gives it.
 */
class IosFileReader(path: String) : Reader() {
    private val source: BufferedSource = FileSystem.SYSTEM.source(path.toPath()).buffer()
    private var pendingLow: Char? = null

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var count = 0
        while (count < length) {
            val low = pendingLow
            if (low != null) {
                buffer[offset + count++] = low
                pendingLow = null
                continue
            }
            if (source.exhausted()) break
            val codePoint = source.readUtf8CodePoint()
            if (codePoint >= SUPPLEMENTARY_START) {
                val shifted = codePoint - SUPPLEMENTARY_START
                buffer[offset + count++] = (HIGH_SURROGATE_START + (shifted shr TEN_BITS)).toChar()
                pendingLow = (LOW_SURROGATE_START + (shifted and TEN_BIT_MASK)).toChar()
            } else {
                buffer[offset + count++] = codePoint.toChar()
            }
        }
        return if (count == 0) -1 else count
    }

    override fun close() = source.use { }

    private companion object {
        const val SUPPLEMENTARY_START = 0x10000
        const val HIGH_SURROGATE_START = 0xD800
        const val LOW_SURROGATE_START = 0xDC00
        const val TEN_BITS = 10
        const val TEN_BIT_MASK = 0x3FF
    }
}
