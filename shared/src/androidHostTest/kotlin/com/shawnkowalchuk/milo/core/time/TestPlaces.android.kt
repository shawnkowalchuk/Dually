package com.shawnkowalchuk.milo.core.time

import com.shawnkowalchuk.milo.core.text.Locale

actual fun testZone(id: String): ZoneId = ZoneId.of(id)

actual fun testLocale(tag: String): Locale = Locale.forLanguageTag(tag)
