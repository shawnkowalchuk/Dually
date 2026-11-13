package com.shawnkowalchuk.milo.core.text

// On Android the shared code's language is java.util.Locale itself (ADR-014, phase 3).

actual typealias Locale = java.util.Locale

actual val rootLocale: Locale get() = Locale.ROOT

actual fun formatText(locale: Locale, pattern: String, vararg args: Any?): String =
    String.format(locale, pattern, *args)
