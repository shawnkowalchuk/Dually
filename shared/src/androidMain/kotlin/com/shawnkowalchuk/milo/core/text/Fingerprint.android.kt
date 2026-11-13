package com.shawnkowalchuk.milo.core.text

import java.security.MessageDigest

actual fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
