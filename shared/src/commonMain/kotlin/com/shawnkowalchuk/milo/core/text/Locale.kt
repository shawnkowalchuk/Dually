package com.shawnkowalchuk.milo.core.text

// The phone's language, as the shared code sees it (ADR-014, phase 3). On Android it is
// java.util.Locale itself, so every caller passes the Locale it always did; on the iPhone it is
// the identifier of Apple's NSLocale.

/** A language and region, for text that follows the phone's language: "en-CA". */
expect class Locale

/** The neutral language, for text that must not depend on the phone's: files, logs, CSV. */
expect val rootLocale: Locale

/**
 * [pattern] with [args] put in, in [locale]'s digits and separators. The pattern is Java's
 * (`%s`, `%d`, `%,d`, `%.1f`, `%1$s`), which Android's string resources use; on Android this is
 * `String.format(locale, pattern, *args)` itself.
 */
expect fun formatText(locale: Locale, pattern: String, vararg args: Any?): String
