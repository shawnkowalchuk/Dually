package com.shawnkowalchuk.milo.core.text

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hex(text: String): String {
    val bytes = text.encodeToByteArray()
    return memScoped {
        val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        if (bytes.isEmpty()) {
            CC_SHA256(null, 0u, digest)
        } else {
            bytes.usePinned { pinned ->
                CC_SHA256(pinned.addressOf(0), bytes.size.toUInt(), digest)
            }
        }
        (0 until CC_SHA256_DIGEST_LENGTH).joinToString("") {
            digest[it].toString(16).padStart(2, '0')
        }
    }
}
