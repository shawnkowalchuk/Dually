package com.shawnkowalchuk.milo.core.text

/**
 * The SHA-256 of [text]'s UTF-8 bytes, as 64 lowercase hex digits (ADR-014, phase 3): Java's
 * MessageDigest on Android, Apple's CommonCrypto on the iPhone. The backup compares these to
 * know which of its documents changed, so both phones must write the same digits.
 */
expect fun sha256Hex(text: String): String
